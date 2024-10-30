package utils;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Constant {
    public static ChromeDriver CHROME_DRIVER;
    public static Actions ACTIONS;
    public static WebDriverWait WAIT;
    public static int WAIT_TIME = 30;
    public static String UNLIMITED_CODE = "0";
    public static String BROWSER_CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
    public static String BROWSER_CHROME_CANARY = "/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary";


}
