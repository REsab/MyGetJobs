package utils;

import org.apache.commons.lang3.StringUtils;

public class ThreadLocalUtil {

	static ThreadLocal<String> browser = new ThreadLocal<>();

	public static String getBrowser() {
		String s = browser.get();
		if (StringUtils.isEmpty(s)) {
			return Constant.BROWSER_CHROME;
		}

		return s;
	}

	public static boolean isChrome() {
		if (Constant.BROWSER_CHROME_CANARY.equals(browser.get())) {
			return false;
		}

		return true;
	}

	public static String getBrowserDriver() {
		if (getBrowser().equals(Constant.BROWSER_CHROME_CANARY)) {
			return "src/main/resources/chromedriver-canary/chromedriver";
		}

		return "src/main/resources/chromedriver";
	}

	public static void setBrowser(String browser1) {
		browser.set(browser1);
	}

	public static void main(String[] args) {
		ThreadLocalUtil.browser.set(Constant.BROWSER_CHROME);
		ThreadLocalUtil.browser.get();
	}
}
