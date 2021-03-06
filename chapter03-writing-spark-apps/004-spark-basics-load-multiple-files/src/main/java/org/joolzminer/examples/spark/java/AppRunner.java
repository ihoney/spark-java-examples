package org.joolzminer.examples.spark.java;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.GroupedData;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.types.DataTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/* static imports for Functional programming */
import static java.util.stream.Collectors.*;
import static org.apache.spark.sql.functions.*;

public class AppRunner {
	
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(AppRunner.class);
	
	public static void main(String[] args) throws IOException {
		SparkConf config = new SparkConf()
								.setAppName("004-spark-basics-load-multiple-files")
								.setMaster("local[*]");

		try (JavaSparkContext sparkContext = new JavaSparkContext(config)) {
			SQLContext sqlContext = new SQLContext(sparkContext);
			
			String inputDir = "./src/main/resources";
			
			DataFrame githubLogDataFrame = sqlContext.read().json(Paths.get("/tmp", "github-archive", "*.json").toString());
			DataFrame pushOperationsDataFrame = githubLogDataFrame.filter("type = 'PushEvent'");
						
			GroupedData pushByActorLogin = pushOperationsDataFrame.groupBy("actor.login");
			DataFrame pushOperationsByActorLogin = pushByActorLogin.count();
			
			DataFrame pushOperationsByActorLoginOrderedByCount = pushOperationsByActorLogin.orderBy(pushOperationsByActorLogin.col("count").desc());
			pushOperationsByActorLoginOrderedByCount.show(50);
			
			/* filtering using values from file */
			String employeesFilename = "ghEmployees.txt";
			Set<String> employees = Files.lines(Paths.get(inputDir, employeesFilename), Charset.defaultCharset())
										.collect(toSet());
			
			Broadcast<Set<String>> employeesBroadcastObj = sparkContext.broadcast(employees);
			
			UDF1<String, Boolean> isEmployeeFn = (String login) -> employeesBroadcastObj.value().contains(login);
			
			
			sqlContext.udf().register("isEmployeeUDF", isEmployeeFn, DataTypes.BooleanType);			
			
			DataFrame employeesPushOperationsByActorLoginOrderedByCount = 
					pushOperationsByActorLoginOrderedByCount
							.filter(callUDF("isEmployeeUDF", col("login")));
																	
			employeesPushOperationsByActorLoginOrderedByCount.show(50);		
		}
		
	}
}
