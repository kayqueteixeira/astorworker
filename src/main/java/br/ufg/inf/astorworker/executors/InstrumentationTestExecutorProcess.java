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

import org.apache.log4j.Logger;
import org.apache.commons.io.FileUtils;

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
	

	public TestResult execute(TestResult tr, Project project, List<String> classesToExecute, File variant,  int waitTime) {
		if(tr == null){
			tr = new TestResult();
			tr.casesExecuted = 0;
			tr.failures = 0;	
		}

		try {
			String mainPackage = project.getMainPackage();
			String location = project.getLocation();

			FileUtils.copyDirectory(new File(ConfigurationProperties.getProperty("defaultsrc")), 
									new File(location+"/app/src/main/java/"));

			FileUtils.copyDirectory(variant, new File(location+"/app/src/main/java/"));
			FileUtils.forceDelete(new File(location+"/app/src/main/java/"+mainPackage.replaceAll("\\.","//")+"/R.java"));
			
			project.installApplicationOnEmulator();
	
			//Running tests
			List<String> output = AndroidToolsExecutorProcess.runInstrumentationTests(project.getTestPackage(), classesToExecute, waitTime);

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
		try {
			for(String line : output) {
				out += line + "\n";
				if (line.startsWith("Tests run:")) {
					String[] s = line.split(":");
					int nrtc = Integer.valueOf(s[1].split(",")[0].trim());
					tr.casesExecuted += nrtc;
					int failing = Integer.valueOf(s[2].trim());
					tr.failures += failing;
					success = true;
				} else if (line.startsWith("OK (")) { //
					String[] s = line.split("\\(");
					int executed = Integer.valueOf(s[1].split("test")[0].trim());
					tr.casesExecuted += executed;
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



	protected String urlArrayToString(URL[] urls) {
		String s = "";
		for (int i = 0; i < urls.length; i++) {
			URL url = urls[i];
			s += url.getPath() + File.pathSeparator;
		}
		return s;
	}
	protected String getProcessError(InputStream str){
		String out = "";
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(str));
			String line;
			while ((line = in.readLine()) != null) {
				out += line + "\n";
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return out;
	}
	
}
