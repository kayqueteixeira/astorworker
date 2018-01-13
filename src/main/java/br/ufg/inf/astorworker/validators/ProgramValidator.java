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
import br.ufg.inf.astorworker.entities.AndroidProject; 

public class ProgramValidator  {
	private static Logger logger = Logger.getLogger(ProgramValidator.class);
	
	public static TestCasesProgramValidationResult validate(File variant) throws Exception {
		TestResult tr = null;

		// Applying variant
		AndroidProject.getInstance().applyVariant(variant);

		//Executing normal test cases
		if(AndroidProject.getInstance().getFailingUnitTestCases() != null) {
			JUnitTestExecutorProcess jtep = new JUnitTestExecutorProcess();
			tr = jtep.executeFailingTests();

			if(tr == null){
				logger.info("There was an error validating the variant");
				return new TestCasesProgramValidationResult(true);
			}
		}
					

		//Executing instrumentation test cases
	 	if(AndroidProject.getInstance().getFailingInstrumentationTestCases() != null){
			InstrumentationTestExecutorProcess itep = new InstrumentationTestExecutorProcess();
			tr = itep.executeFailingTests(tr);

			if(tr == null){
				logger.info("There was an error validating the variant");
				return new TestCasesProgramValidationResult(true);
			}
		}

		logger.info("Number of test cases executed: " + tr.casesExecuted + "\tNumber of failing test cases: " + tr.failures);

		if(tr.wasSuccessful()){
			logger.info("No failing test cases");
			//Execute regression
			return runRegression(variant);
		}

		return new TestCasesProgramValidationResult(tr, tr.wasSuccessful(), false);
	}


	private static TestCasesProgramValidationResult runRegression(File variant) throws Exception {
		logger.info("Running regression");
		TestResult trregression = null;

		//Executing normal test cases
		if(AndroidProject.getInstance().unitRegressionTestCasesExist()) {
			JUnitTestExecutorProcess jtep = new JUnitTestExecutorProcess();
			trregression = jtep.executeRegression();

			if(trregression == null){
				logger.info("There was an error validating the variant");
				return new TestCasesProgramValidationResult(true);
			}
		}

		//Executing instrumentation test cases
	 	if(AndroidProject.getInstance().instrumentationRegressionTestCasesExist()) {
			InstrumentationTestExecutorProcess itep = new InstrumentationTestExecutorProcess();
			trregression = itep.executeRegression(trregression);

			if(trregression == null){
				logger.info("There was an error validating the variant");
				return new TestCasesProgramValidationResult(true);
			}
		}

		logger.info("Number of test cases executed: "+trregression.casesExecuted+"\tNumber of failing test cases: "+trregression.failures);
		
		return new TestCasesProgramValidationResult(trregression, trregression.wasSuccessful(), true);			
	}
}