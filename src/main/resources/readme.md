
# about chromedriver

[//]: # (todo 2 浏览器驱动下载文档，可能需要vpn)

1. 浏览器输入查看当前的版本  chrome://version/



## 浏览器驱动下载链接
2。大于 114  的版本   https://googlechromelabs.github.io/chrome-for-testing/#stable

3。 小于114  的版本 https://developer.chrome.com/docs/chromedriver/downloads?hl=zh-cn


## 下载完成后
放到 src/main/resources/chromedriver 目录下
并在 代码中(SeleniumUtil)指定驱动位置

System.setProperty("webdriver.chrome.driver", "src/main/resources/xxxxxxxx换成对应的目录文件xxxxxxxxxxxxxxxxx");

