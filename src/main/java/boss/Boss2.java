package boss;

import lombok.SneakyThrows;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Job;
import utils.JobUtils;
import utils.SeleniumUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static utils.Constant.CHROME_DRIVER;
import static utils.Constant.WAIT;

/**
 * @author loks666 Boss直聘自动投递
 */
public class Boss2 {
	private static final Logger log = LoggerFactory.getLogger(Boss2.class);
	static Integer page = 1;
	static String homeUrl = "https://www.zhipin.com";
	static String baseUrl = "https://www.zhipin.com/web/geek/job?";
	static List<String> blackCompanies;
	static List<String> blackRecruiters;
	static List<String> blackJobs;

	static List<String> bossStatusBlackList;
	static List<String> bossStatusWhiteList;
	static List<Job> returnList = new ArrayList<>();
	static String dataPath = "./src/main/java/boss/data.json";
	static String cookiePath = "./src/main/java/boss/cookie.json";
	static final int noJobMaxPages = 13; // 无岗位最大页数
	static int noJobPages;
	static int lastSize;
	static BossConfig config = BossConfig.init();

	public static void main(String[] args) {
		loadData(dataPath);
		SeleniumUtil.initDriver();
		Date start = new Date();
		login();
		String searchUrl = "https://www.zhipin.com/web/geek/job-recommend";
		endSubmission:
		for (String keyword : config.getKeywords()) {
			page = 1;
			noJobPages = 0;
			lastSize = -1;
			while (true) {
				log.info("投递【{}】关键词第【{}】页", keyword, page);
				String url = searchUrl;
				int startSize = returnList.size();
				Integer resultSize = null;
				try {
					// resultSize = resumeSubmission(url );
					CHROME_DRIVER.get("https://www.zhipin.com/web/geek/job-recommend?ka=header-job-recommend");
					WAIT.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='rec-job-list']")));
					findJobs(url);
				} catch (Exception e) {
					resultSize = -2;
				}

				if (resultSize == -1) {
					log.info("今日沟通人数已达上限，请明天再试");
					break endSubmission;
				}
				if (resultSize == -2) {
					log.info("出现异常访问，请手动过验证后再继续投递...");
					break endSubmission;
				}
				if (resultSize == startSize) {
					noJobPages++;
					if (noJobPages >= noJobMaxPages) {
						log.info("【{}】关键词已经连续【{}】页无岗位，结束该关键词的投递...", keyword, noJobPages);
						break;
					} else {
						log.info("【{}】关键词第【{}】页无岗位,目前已连续【{}】页无新岗位...", keyword, page, noJobPages);
					}
				} else {
					lastSize = resultSize;
					noJobPages = 0;
				}
				page++;
			}
		}
		Date end = new Date();
		log.info(returnList.isEmpty() ? "未发起新的聊天..." : "新发起聊天公司如下:\n{}",
				returnList.stream().map(Object::toString).collect(Collectors.joining("\n")));
		long durationSeconds = (end.getTime() - start.getTime()) / 1000;
		long minutes = durationSeconds / 60;
		long seconds = durationSeconds % 60;
		String message = "共发起 " + returnList.size() + " 个聊天,用时" + minutes + "分" + seconds + "秒";
		//        saveData(dataPath);
		log.info(message);
		CHROME_DRIVER.close();
		CHROME_DRIVER.quit();
	}

	private static void findJobs(String url) {

		log.debug("开始访问【{}】", url);
		// rec-job-list

		List<WebElement> jobCards = CHROME_DRIVER.findElements(By.cssSelector("li.job-card-box"));

		int index = 0;

		for (WebElement jobCard : jobCards) {

			index++;
			log.debug("第{}个", index);
			Job job = new Job();

			try {
				WebElement jobName = jobCard.findElement(By.cssSelector("a.job-name"));
				WebElement boosName = jobCard.findElement(By.cssSelector("span.boss-name"));
				WebElement jobSalary = jobCard.findElement(By.cssSelector("span.job-salary"));

				try {
					WebElement isOnLine = jobCard.findElement(By.cssSelector("boss-online-icon"));
				} catch (Exception e) {

				}
				String text1 = jobSalary.getText();
				String salary = changeSalary(text1);
				job.setCompanyTag(boosName.getText());
				job.setSalary(salary);
				job.setJobName(jobName.getText());
			} catch (Exception e) {
				log.error("解析失败 job", e);
			}

			boolean b = checkJob(job);
			if (!b) {
				log.info("【{}】[{}]，跳过...", job.getJobName(), job.getSalary());
				continue;
			}

			try {
				// 查看岗位详情
				jobCard.click();
				SeleniumUtil.sleepByMilliSeconds(500);
				// job-detail-box
				WebElement jobDetail = CHROME_DRIVER.findElement(By.cssSelector("div.job-detail-box"));
				WebElement opBtnChat = jobDetail.findElement(By.cssSelector("a.op-btn-chat"));
				if (!opBtnChat.getText().equals("立即沟通")) {
					continue;
				}

				// boss 活跃时间
				try {
					// boss-active-time
					WebElement bossActiveTime = jobDetail.findElement(By.cssSelector("span.boss-active-time"));
					job.setBossActiveTime(bossActiveTime.getText());
				} catch (Exception e) {
					log.error("解析失败", e);
				}

				// 打开聊天窗口
				try {
					log.info("立即沟通...");
					opBtnChat.click();
					SeleniumUtil.sleepByMilliSeconds(500);
					// 是否有继续沟通
					WebElement btnMsgContinue = CHROME_DRIVER.findElement(By.cssSelector("[class*='sure-btn']"));
					btnMsgContinue.click();
				} catch (Exception ignore) {
					log.debug("没有继续沟通按钮");
				}

				// 输入沟通内容
				WebElement input = WAIT.until(
						ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@id='chat-input']")));
				input.click();
				SeleniumUtil.sleepByMilliSeconds(500);
				try {
					// 是否出现不匹配的对话框
					WebElement element11 = CHROME_DRIVER.findElement(By.xpath("//div[@class='dialog-container']"));
					if ("不匹配".equals(element11.getText())) {
						continue;
					}
				} catch (Exception e) {
					log.debug("岗位匹配，下一步发送消息...");
				}

				input.sendKeys(config.getSayHi());
				WebElement send = WAIT.until(
						ExpectedConditions.presenceOfElementLocated(By.xpath("//button[@type='send']")));
				send.click();

				// 总结日志

				log.info("投递【{}】公司，【{}】职位，招聘官:【{}】,在线：【{}】",
						job.getJobName() == null ? "未知公司: " : job.getJobName(), job.getSalary(), "--",
						job.getBossActiveTime());
				SeleniumUtil.sleepByMilliSeconds(5000);
			} catch (Exception e) {
				log.error("查看详情失败", e);
			}

			// 返回列表页
			CHROME_DRIVER.navigate().back();
			SeleniumUtil.sleepByMilliSeconds(500);
			break;
		}

		//	刷新页面
		CHROME_DRIVER.navigate().refresh();
		SeleniumUtil.sleepByMilliSeconds(500);
		findJobs(url);
	}

	private static String changeSalary(String text1) {

		//-K·薪  20-40k·14薪
		//-K·薪  25-40k·16薪
		//-K·薪  28-55k·16薪
		//-K 				19-23k
		//-K·薪  28-35k·13薪

		text1 = text1.replace("\uE032", "1");
		text1 = text1.replace("\uE033", "2");
		text1 = text1.replace("\uE034", "3");
		text1 = text1.replace("\uE035", "4");
		text1 = text1.replace("\uE036", "5");
		text1 = text1.replace("\uE037", "6");
		text1 = text1.replace("\uE038", "7");
		text1 = text1.replace("\uE039", "8");
		text1 = text1.replace("\uE03A", "9");
		text1 = text1.replace("\uE031", "0");

		// text1 = text1.replace("", "3");
		// text1 = text1.replace("\uE035", "4");
		// text1 = text1.replace("\uE036", "5");
		// text1 = text1.replace("", "6");
		// text1 = text1.replace("", "7");
		// text1 = text1.replace("", "8");
		// text1 = text1.replace("", "9");
		// text1 = text1.replace("\uE031", "0");

		return text1;
	}

	private static void loadData(String path) {
		try {
			String json = new String(Files.readAllBytes(Paths.get(path)));
			parseJson(json);
		} catch (IOException e) {
			log.error("读取【{}】数据失败！", path);
		}
	}

	private static void parseJson(String json) {
		JSONObject jsonObject = new JSONObject(json);
		blackCompanies = jsonObject.getJSONArray("blackCompanies").toList().stream().map(Object::toString)
				.collect(Collectors.toList());
		blackRecruiters = jsonObject.getJSONArray("blackRecruiters").toList().stream().map(Object::toString)
				.collect(Collectors.toList());
		blackJobs = jsonObject.getJSONArray("blackJobs").toList().stream().map(Object::toString)
				.collect(Collectors.toList());
		// bossStatus = jsonObject.getJSONArray("bossStatus").toList().stream().map(Object::toString).collect
		// (Collectors.toList());
		bossStatusBlackList = jsonObject.getJSONArray("bossStatusBlackList").toList().stream().map(Object::toString)
				.collect(Collectors.toList());
		bossStatusWhiteList = jsonObject.getJSONArray("bossStatusWhiteList").toList().stream().map(Object::toString)
				.collect(Collectors.toList());
	}

	private static boolean checkJob(Job job) {

		try {
			String aa = job.getSalary();
			String substring = aa.substring(0, aa.indexOf("K"));
			String[] split = substring.split("-");
			Integer minSalary = Integer.valueOf(split[0]);
			Integer maxSalary = Integer.valueOf(split[1]);
			if (minSalary < config.getMinSalary()) {
				return false;
			}
			if (maxSalary > config.getMaxSalary()) {
				return true;
			}
		} catch (Exception e) {
			log.error("e", e);
			log.error("error getSalary", job.getSalary());
		}
		log.info("忽略{}, {}", job.getJobName(), job.getSalary());
		return false;
	}

	@SneakyThrows
	private static void login() {
		log.info("打开Boss直聘网站中...");
		CHROME_DRIVER.get(homeUrl);
		if (SeleniumUtil.isCookieValid(cookiePath)) {
			SeleniumUtil.loadCookie(cookiePath);
			CHROME_DRIVER.navigate().refresh();
			SeleniumUtil.sleep(2);
		}

		if (isLoginRequired()) {
			log.error("cookie失效，尝试扫码登录...");
			scanLogin();
		}
	}

	private static boolean isLoginRequired() {
		try {
			String text = CHROME_DRIVER.findElement(By.className("btns")).getText();
			return text != null && text.contains("登录");
		} catch (Exception e) {
			log.info("cookie有效，已登录...");
			return false;
		}
	}

	@SneakyThrows
	private static void scanLogin() {
		CHROME_DRIVER.get(homeUrl + "/web/user/?ka=header-login");
		log.info("等待登陆..");
		// Thread.sleep(20000);
		WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//a[@ka='header-home-logo']")));
		boolean login = false;
		while (!login) {
			try {
				WAIT.until(
						ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"header\"]/div[1]/div[1]/a")));
				WAIT.until(ExpectedConditions.presenceOfElementLocated(
						By.xpath("//*[@id=\"wrap\"]/div[2]/div[1]/div/div[1]/a[2]")));
				login = true;
				log.info("登录成功！保存cookie...");
			} catch (Exception e) {
				log.error("登陆失败，两秒后重试...");
			} finally {
				SeleniumUtil.sleep(2);
			}
		}
		SeleniumUtil.saveCookie(cookiePath);
	}
}
