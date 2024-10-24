package boss;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.crypto.signers.Ed25519ctxSigner;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Job;
import utils.SeleniumUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
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
	static final int noJobMaxPages = 5; // 一次性加载出来页数
	static int noJobPages;
	static int lastSize;
	static BossConfig config = BossConfig.init();

	static int indexJobCard = 0;
	static Integer resultSize = 0;

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

				try {
					CHROME_DRIVER.get("https://www.zhipin.com/web/geek/job-recommend?ka=header-job-recommend");
					WAIT.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='rec-job-list']")));

					// 选择城市
					WebElement city = CHROME_DRIVER.findElement(By.xpath("//span[@class='text-content']"));
					city.click();

					// 执行滚动并等待一段时间让新内容加载

					for (int i = 0; i < noJobMaxPages; i++) {
						((JavascriptExecutor) CHROME_DRIVER).executeScript(
								"window.scrollTo(0, document.body.scrollHeight);");
						SeleniumUtil.sleepByMilliSeconds(1500);
					}
					// 返回顶部
					((JavascriptExecutor) CHROME_DRIVER).executeScript("window.scrollTo(0, 0);");

					findJobs(url);
				} catch (Exception e) {
					resultSize = -2;
					e.printStackTrace();
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

		// 执行滚动并等待一段时间让新内容加载
		log.debug("开始访问【{}】", url);
		// rec-job-list
		List<WebElement> jobCards = CHROME_DRIVER.findElements(By.cssSelector("li.job-card-box"));
		log.debug("jobcards size {}", jobCards.size());

		int index = 0;

		for (WebElement jobCard : jobCards) {

			index++;
			log.debug("第{}个", index);
			Job job = new Job();
			try {
				{

					jobCard.click();
					SeleniumUtil.sleepByMilliSeconds(500);

					try {
						WebElement jobName = jobCard.findElement(By.cssSelector("a.job-name"));
						WebElement jobSalary = jobCard.findElement(By.cssSelector("span.job-salary"));
						WebElement company = jobCard.findElement(By.cssSelector("span.boss-name"));
						WebElement bossName = CHROME_DRIVER.findElement(By.xpath("//h2[@class='name']"));

						String text1 = jobSalary.getText();
						String salary = changeSalary(text1);
						job.setRecruiter(bossName.getText());
						job.setCompanyTag(company.getText());
						job.setSalary(salary);
						job.setJobName(jobName.getText());
						job.setCompanyName(company.getText());

						try {
							WebElement area = CHROME_DRIVER.findElement(By.xpath("//p[@class='job-address-desc']"));
							job.setJobArea(area.getText());
						} catch (Exception e) {

						}
					} catch (Exception e) {
						log.error("解析失败 job", e);
					}

					if (!checkJob(job)) {
						log.info("【{}】[{}]，跳过...", job.getJobName(), job.getSalary());
						continue;
					}

					try {
						// 查看岗位详情
						// job-detail-box
						WebElement jobDetail = CHROME_DRIVER.findElement(By.cssSelector("div.job-detail-box"));
						WebElement opBtnChat = jobDetail.findElement(By.cssSelector("a.op-btn-chat"));
						if (!opBtnChat.getText().equals("立即沟通")) {
							continue;
						}

						boolean isMsgTo = checkOnLine(job);
						if (!isMsgTo) {
							log.info("ignore job: " + job.getJobName() + job.getBossActiveTime());
							SeleniumUtil.sleepByMilliSeconds(800);
							continue;
						}

						// 打开聊天窗口
						try {
							log.info("立即沟通...");
							SeleniumUtil.sleepByMilliSeconds(1500);

							opBtnChat.click();
							SeleniumUtil.sleepByMilliSeconds(500);
							// 不继续沟通
							WebElement btnMsgNoContinue = CHROME_DRIVER.findElement(
									By.cssSelector("[class*='cancel-btn']"));
							btnMsgNoContinue.click();
						} catch (Exception ignore) {
							log.debug("没有继续沟通按钮");
						}

						// // 输入沟通内容
						// WebElement input = WAIT.until(
						// 		ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@id='chat-input']")));
						// input.click();
						// SeleniumUtil.sleepByMilliSeconds(500);
						// try {
						// 	// 是否出现不匹配的对话框
						// 	WebElement element11 = CHROME_DRIVER.findElement(
						// 			By.xpath("//div[@class='dialog-container']"));
						// 	if ("不匹配".equals(element11.getText())) {
						// 		continue;
						// 	}
						// } catch (Exception e) {
						// 	log.debug("岗位匹配，下一步发送消息...");
						// }
						//
						// input.sendKeys(config.getSayHi());
						// WebElement send = WAIT.until(
						// 		ExpectedConditions.presenceOfElementLocated(By.xpath("//button[@type='send']")));
						// send.click();

						// 总结日志

						log.info("投递【{}】公司，【{}】职位，招聘官:【{}】,在线：【{}】",
								job.getCompanyName() == null ? "未知公司: " : job.getJobName(), job.getSalary(),
								job.getRecruiter(), job.getBossActiveTime());

						returnList.add(job);
						noJobPages = 0;

						SeleniumUtil.sleepByMilliSeconds(5000);
					} catch (Exception e) {
						log.error("查看详情失败", e);
					}
				}
			} catch (Exception e) {
				log.warn("第{}个 异常 {} , error : ", index, job, e.getMessage());
			}
		}
	}

	private static String changeSalary(String text1) {

		//-K·薪  20-40k·14薪
		//-K·薪  25-40k·16薪
		//-K·薪  28-55k·16薪
		//-K 				19-23k
		//-K·薪  28-35k·13薪
		log.debug("changeSalary {}", text1);

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

		log.debug("text1 {}", text1);
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

		boolean companyMatch = false;
		boolean salaryMatch = false;
		boolean jobNameMatch = false;
		boolean jobBlackNameMatch = true;

		// 公司
		try {

			String companyName = job.getCompanyName();
			if (StringUtils.isNotBlank(companyName)) {

				if (!blackCompanies.stream().anyMatch(companyName::contains)) {
					log.debug("blackCompanies,, {}", companyName);
					companyMatch = true;
				}
			}
		} catch (Exception e) {
			log.error("error company :{}", e);
		}

		// 工资
		try {
			String salary = job.getSalary();
			if (salary == null) {
				log.error("error getSalary2 {}", job);
			}
			String substring = salary.substring(0, salary.indexOf("K"));
			String[] split = substring.split("-");
			Integer minSalary = Integer.valueOf(split[0]);
			Integer maxSalary = Integer.valueOf(split[1]);
			if (minSalary > config.getMinSalary() && maxSalary > config.getMaxSalary()) {
				salaryMatch = true;
			} else {
				log.debug("getMinSalary,, {}", salary);
			}
		} catch (Exception e) {
			log.error("e", e);
			log.error("error getSalary", job.getSalary());
		}

		// 工作名称
		List<String> jobNames = new ArrayList<>();
		jobNames.add("java");
		jobNames.add("Java");
		jobNames.add("JAVA");
		jobNames.add("开发");
		jobNames.add("研发");
		if (jobNames.stream().anyMatch(a -> job.getJobName().contains(a))) {
			jobNameMatch = true;
		} else {
			log.debug("jobNameNoMatch {}", job.getJobName());
		}

		List<String> jobNames1 = new ArrayList<>();
		jobNames1.add("总监");
		jobNames1.add("客服");
		jobNames1.add("android");
		jobNames1.add("Android");
		jobNames1.add("");

		if (jobNames1.stream().anyMatch(a -> job.getJobName().contains(a))) {
			jobBlackNameMatch = false;
			log.debug("jobBlackNameMatch {}", job.getJobName());
		}

		log.debug("job check  info :{}, {} , {}, {}  ", companyMatch, jobNameMatch, salaryMatch, jobBlackNameMatch);
		log.debug("{}, {}", job.getJobName(), job.getSalary());

		return companyMatch && jobNameMatch && salaryMatch && jobBlackNameMatch;
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

	public static boolean checkOnLine(Job job) {
		// boss-online-tag
		try {
			// 在线
			WebElement infoPublic = CHROME_DRIVER.findElement(By.className("boss-online-tag"));
			String text = infoPublic.getText();
			job.setBossActiveTime(text);
			return true;
		} catch (Exception e) {
		}
		//
		try {
			// 近期活跃

			WebElement infoPublic2 = CHROME_DRIVER.findElement(By.xpath("//span[@class='boss-active-time']"));
			String text = infoPublic2.getText();
			boolean contains = bossStatusWhiteList.contains(text);
			boolean contains1 = bossStatusBlackList.contains(text);

			if (contains) {
				job.setBossActiveTime(text);
				return true;
			}

			if (contains1) {
				// # 滚动到底部
				CHROME_DRIVER.executeScript("arguments[0].style.fontSize = '33px';", infoPublic2);
				CHROME_DRIVER.executeScript("arguments[0].style.color = 'red';", infoPublic2);
				CHROME_DRIVER.executeScript("arguments[0].scrollIntoView(false);", infoPublic2);
				SeleniumUtil.sleep(1);
				return false;
			}
			log.debug("忽略bossStatus: {}", text);
		} catch (Exception e) {
		}
		job.setBossActiveTime("-");
		log.error("找不到boss状态");
		return false;
	}
}
