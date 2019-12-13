import java.net.URL

import io.appium.java_client.AppiumDriver
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.DesiredCapabilities

class test {
  implicit var driver: AppiumDriver[WebElement] = _
  var url = "http://127.0.0.1:4723/wd/hub"
  val capabilities = new DesiredCapabilities()
  capabilities.setCapability("platformName", "Android")
  capabilities.setCapability("deviceName", "43b24d7a")
  capabilities.setCapability("app", "")
  capabilities.setCapability("clearSystemFiles", true)
  capabilities.setCapability("appActivity", "com.mizholdings.orange.function.splash.SplashActivity")
  capabilities.setCapability("appPackage", "com.mizholdings.me2.cloudclass.debug")
  capabilities.setCapability("noSign", true)


  driver = new AndroidDriver[WebElement](new URL(url), capabilities)
}
