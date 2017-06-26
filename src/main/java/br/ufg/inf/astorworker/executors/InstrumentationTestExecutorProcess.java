package br.ufg.inf.astorworker.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.log4j.Logger;

import fr.inria.astor.core.validation.entity.TestResult;
import fr.inria.astor.core.setup.ConfigurationProperties;
import br.ufg.inf.astorworker.entity.Project;

/**
 * Process-based program variant validation
 * 
 * @author Kayque de S. Teixeira
 * 
 */
public class  InstrumentationTestExecutorProcess {
	private Logger logger = Logger.getLogger(InstrumentationTestExecutorProcess.class);
	boolean avoidInterruption = false;
	

	public TestResult execute(TestResult tr, Project project, List<String> classesToExecute) {
		if(tr == null){
			tr = new TestResult();
			tr.casesExecuted = 0;
			tr.failures = 0;	
		}

		try {
			//Running tests
			List<String> output = AndroidToolsExecutorProcess.runInstrumentationTests(project.getProjectLocation(), project.getInstrumentationTestTask(), classesToExecute);

			tr = getTestResult(tr, output);
			
			return tr;
		} catch ( IOException |InterruptedException |IllegalThreadStateException | IllegalStateException ex) {
			logger.info("The Process that runs instrumentation test cases had problems: " + ex.getMessage());
		}
		return null;
	}


	public TestResult executeRegression(TestResult tr, Project project) {
		if(tr == null){
			tr = new TestResult();
			tr.casesExecuted = 0;
			tr.failures = 0;	
		}

		try {
			//Running tests
			List<String> output = AndroidToolsExecutorProcess.runInstrumentationTests(project.getProjectLocation(), project.getInstrumentationTestTask());

			tr = getTestResult(tr, output);
			
			return tr;
		} catch ( IOException |InterruptedException |IllegalThreadStateException | IllegalStateException ex) {
			logger.info("The Process that runs instrumentation test cases had problems: " + ex.getMessage());
		}
		return null;
	}

	/**
	 * This method analyze the output of the junit executor and return an entity called TestResult with
	 * the result of the test execution
	 * 
	 * @param p
	 * @return
	 */
	protected TestResult getTestResult(TestResult tr, List<String> output) {
		boolean success = false;
		String out = "";
		Pattern testPattern = Pattern.compile("([a-zA-Z0-9._])+(\\s>\\s)([a-zA-Z0-9._])+(\\[.*?\\]\\s*)(\\e\\[31m|\\e\\[32m)(FAILED|SUCCESS)(\\s\\e\\[0m)\\s*");
		try {
			for(String line : output) {
				out += line + "\n";
				Matcher m = testPattern.matcher(line);

				if (m.matches()) {
					tr.casesExecuted++;

					String result = m.group(6);
					if(result.equals("FAILED"))
						tr.failures++;
					success = true;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (success)
			return tr;
		else {
			logger.info("The Process that runs instrumentation test cases had problems reading the validation process\n output: \n" + out);
			return null;
		}
	}
	
}
