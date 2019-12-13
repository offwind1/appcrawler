import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.net.MalformedURLException;
import java.net.URL;

public class ttt {

    public static void main(String... args) throws MalformedURLException {
        URL url = new URL("http://127.0.0.1:4723/wd/hub");
        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setCapability("platformName", "Android");
        capabilities.setCapability("deviceName", "43b24d7a");
        capabilities.setCapability("app", "");
        capabilities.setCapability("clearSystemFiles", true);
        capabilities.setCapability("appActivity", "com.mizholdings.orange.function.splash.SplashActivity");
        capabilities.setCapability("appPackage", "com.mizholdings.me2.cloudclass.debug");
        capabilities.setCapability("noSign", true);

        AndroidDriver<WebElement> driver = new AndroidDriver<WebElement>(url, capabilities);
        System.out.println(driver.getPageSource());
    }


}
