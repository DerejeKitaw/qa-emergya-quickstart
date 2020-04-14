package stepDefinitions;


import java.lang.reflect.Method;
import org.apache.commons.lang3.tuple.ImmutablePair;
import com.emergya.pageObjects.EmergyaContactPage;
import com.emergya.pageObjects.EmergyaMainPage;
import com.emergya.pageObjects.GoogleMainPage;
import com.emergya.selenium.testSet.DefaultTestSet;

import com.emergya.utils.CrossBrowserInitialization;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import io.cucumber.java.Scenario;
import runner.TestMethodGenerator;
import runner.TestNGCucumberRunnerClass;

/**
 * 
 * @author Antonio Pérez <aoviedo@emergya.com>
 * This abstract class is equivalent to the class BasicTestSet for tests not executed using cucumber.
 * In this class we will include all the page objects that we will need as protected parameters
 * However, we have a very interesting constructor. This constructor calls to its parent DefaultTestSet's constructor
 * and then executes the method before from its parent class DefaultTest which will the method that will initialize
 * the driver like in the project. But in this case, we have to do these actions here to provide a functional
 * driver, why?
 * This is the class that all our classes that define steps for our feature files (all our step definition classes)
 * must extend.
 */

public abstract class BasicStepsDefinitions extends DefaultTestSet{
	/**
	 * For instance, in the test we provide as example we need these 3 pageObjects that have been defined using
	 * our selenium handler and the framework. When we extend this class, all our step definitions will have these
	 * available so we will be able to use its methods to define our steps
	 */
	protected GoogleMainPage googleMainPage;
	protected EmergyaMainPage emergyaMainPage;
	protected EmergyaContactPage emergyaContactPage;
	
	/**
	 * In our constructor we have to call super.before() because in the method before of the class
	 * DefaultTestSet is where we get our driver (remote or local) so we have to call it explicitly
	 * for cucumber to have it available. In order to do so, we need an object of type Method (that
	 * we get from a method coded far below and the remote parameters needed to connect to CrossBrowserTesting
	 * which we get by calling the method super.remoteParams()
	 */
	
	public BasicStepsDefinitions() {
		super();
		Object[] remoteParams = null;
		Method method = null;
		ImmutablePair<String, String> featureScenario = TestNGCucumberRunnerClass.scenarios.remove(0);
		try {
			log.info("[log-cucumberClasses] " + this.getClass().getSimpleName()
                + " - Start isReady method");
			log.info("[log-cucumberClasses] Executing feature" + featureScenario.getValue()
	                + " - scenario: "+featureScenario.getKey());
			/**
			 * We get the parameters we will need later to pass to super.before (DefaultTestSet.before)
			 */
			remoteParams = super.remoteParams();
			/**
			 * We have our current feature and scenario, with those details we will build
			 * a temporal class and method to pass to super.before (DefaultTestSet.before)
			 */
			String tempClassName = featureScenario.getValue().replace(" ", "_");
			tempClassName = tempClassName.replaceAll("[^A-Za-z]+", "");
			String testMethodName = featureScenario.getKey().replace(" ", "_");
			testMethodName = testMethodName.replaceAll("[^A-Za-z]+", "");
			testMethodName = "feature_"+tempClassName+"_"+testMethodName;
			//We create and object of type testMethodGenerator that handles the creation and compilation
			//of the temporal class we need with the method we will pass onto super.before
			TestMethodGenerator testMethodGenerator = new TestMethodGenerator(tempClassName, testMethodName);
			//Once created, we try to get the method
			method = testMethodGenerator.getTestMethod(testMethodName);
		} catch (NoSuchMethodException e) {
			//In case, there is an exception, we will give it a last try and try to get the name
			//of a predefined method using this.cucumberRemoteTest and our test in CrossBrowserTesting will
			//be called that
			log.error("[log-cucumberClasses] " + this.getClass().getName()
		            + " Constructor: "+e);
			try {
				method = this.cucumberRemoteTest();
			} catch (NoSuchMethodException | SecurityException e1) {
				e1.printStackTrace();
			}
		} catch (SecurityException e) {
			//In case, there is an exception, we will give it a last try and try to get the name
			//of a predefined method using this.cucumberRemoteTest and our test in CrossBrowserTesting will
			//be called that
			log.error("[log-cucumberClasses] " + this.getClass().getName()
		            + " Constructor: "+e);
			try {
				method = this.cucumberRemoteTest();
			} catch (NoSuchMethodException | SecurityException e1) {
				e1.printStackTrace();
			}
		}catch(Exception e){
			log.error("[log-cucumberClasses] " + this.getClass().getName()
		            + " Constructor: "+e);
		}
		//Finally, we call super.before which is where DefaultTestSet creates and initializes our Webdriver
		// and makes it available
		super.before(method,remoteParams);
    }
	
	/**
	 * This method is used to get an object of type Method that we can use to call super.before,
	 * we have to call this method to get our driver@Before. It is an alternative to using
	 * class TestMethodGenerator, we are not using this by default.
	 * @return a Method object needed to call super.before and get our remote driver
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	public Method cucumberRemoteTest() throws NoSuchMethodException, SecurityException {
		String nameofCurrMethod = new Throwable() 
                .getStackTrace()[0] 
                .getMethodName();
		return BasicStepsDefinitions.class.getDeclaredMethod(nameofCurrMethod);
	}
	/**
	 * This method has to be executed in every StepsDefinition class inside the method with
	 * the annotation @After that way we set the result of the test in CrossBrowserTesting
	 * @param scenario
	 */
	public void afterScenario(Scenario scenario) {
		if(scenario.isFailed()) {
			log.info("======FAIL=====");
            setScore(driver.getSessionId().toString(), "fail");
		}else {
			log.info("======PASS=====");
            setScore(driver.getSessionId().toString(), "pass");
		}
		driver.quit();
	}
    // ************************ Private Methods *************************
    /**
     * This is the method that does the real task of sending a request to CrossBrowserTesting's API
     * to set our tests pass or fail
     * @param seleniumTestId
     * @param score
     * @return
     */
    private JsonNode setScore(String seleniumTestId, String score) {
        // Mark a Selenium test as Pass/Fail
        HttpResponse<JsonNode> response = null;

        try {
            response = Unirest.put(
                    "http://crossbrowsertesting.com/api/v3/selenium/{seleniumTestId}")
                    .basicAuth(
                            CrossBrowserInitialization.getInstance().getRemoteUserName(),
                            CrossBrowserInitialization.getInstance().getRemoteUserKey())
                    .routeParam("seleniumTestId", seleniumTestId)
                    .field("action", "set_score").field("score", score)
                    .asJson();
        } catch (UnirestException e) {
            log.error(e.getMessage());
        }
        return response.getBody();
    }
}
