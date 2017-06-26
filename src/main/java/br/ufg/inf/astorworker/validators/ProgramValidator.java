package br.ufg.inf.astorworker.validators;

import java.io.File;
import java.util.Arrays;
import java.net.MalformedURLException;

import org.apache.log4j.Logger;
import org.apache.commons.io.FileUtils;

import fr.inria.astor.core.validation.entity.TestResult;
import fr.inria.astor.core.validation.validators.TestCasesProgramValidationResult;
import fr.inria.astor.core.setup.ConfigurationProperties;
import br.ufg.inf.astorworker.executors.InstrumentationTestExecutorProcess;
import br.ufg.inf.astorworker.executors.JUnitTestExecutorProcess; 
import br.ufg.inf.astorworker.entity.Project; 
import br.ufg.inf.astorworker.executors.CommandExecutorProcess;

public class ProgramValidator  {
	private static Logger logger = Logger.getLogger(ProgramValidator.class);
	
	public static TestCasesProgramValidationResult validate(Project project, File variant) throws Exception {
		TestResult tr = null;

		// Applying variant
		String location = project.getLocation();

		FileUtils.copyDirectory(new File(ConfigurationProperties.getProperty("defaultsrc")), 
									new File(location+"/app/src/main/java/"));

		FileUtils.copyDirectory(variant, new File(location+"/app/src/main/java/"));

		//Executing normal test cases
		if(project.getFailing() != null) {
			JUnitTestExecutorProcess jtep = new JUnitTestExecutorProcess();
			tr = jtep.execute(project, Arrays.asList(project.getFailing().split(":")));

			if(tr == null){
				logger.info("There was an error validating the variant");
				return new TestCasesProgramValidationResult(true);
			}
		}
					

		//Executing instrumentation test cases
	 	if(project.getInstrumentationFailing() != null){
			InstrumentationTestExecutorProcess itep = new InstrumentationTestExecutorProcess();
			tr = itep.execute(tr, project, Arrays.asList(project.getInstrumentationFailing().split(":")));

			if(tr == null){
				logger.info("There was an error validating the variant");
				return new TestCasesProgramValidationResult(true);
			}
		}

		logger.info("Number of test cases executed: "+tr.casesExecuted+"\tNumber of failing test cases: "+tr.failures);

		if(tr.wasSuccessful()){
			logger.info("No failing test cases");
			//Execute regression
			return runRegression(project, variant);
		}

		return new TestCasesProgramValidationResult(tr, tr.wasSuccessful(), false);
	}


	private static TestCasesProgramValidationResult runRegression(Project project, File variant) throws MalformedURLException {
		logger.info("Running regression");
		TestResult trregression = null;

		//Executing normal test cases
		if(project.unitRegressionTestCasesExist()) {
			JUnitTestExecutorProcess jtep = new JUnitTestExecutorProcess();
			trregression = jtep.executeRegression(project);

			if(trregression == null){
				logger.info("There was an error validating the variant");
				return new TestCasesProgramValidationResult(true);
			}
		}

		//Executing instrumentation test cases
	 	if(project.instrumentationRegressionTestCasesExist()) {
			InstrumentationTestExecutorProcess itep = new InstrumentationTestExecutorProcess();
			trregression = itep.executeRegression(trregression, project);

			if(trregression == null){
				logger.info("There was an error validating the variant");
				return new TestCasesProgramValidationResult(true);
			}
		}

		logger.info("Number of test cases executed: "+trregression.casesExecuted+"\tNumber of failing test cases: "+trregression.failures);
		
		return new TestCasesProgramValidationResult(trregression, trregression.wasSuccessful(), true);			
	}
}