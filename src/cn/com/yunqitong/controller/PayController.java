package cn.com.yunqitong.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import cn.com.yunqitong.service.PayService;
import cn.com.yunqitong.util.HttpsUtil;
import cn.com.yunqitong.util.IpUtil;

@RequestMapping(value = "/pay")
@Controller
public class PayController {
	@Autowired
	private PayService payService;
	protected Logger log = Logger.getLogger(PayService.class);

	/**
	 * 微信支付方式发起支付请求
	 * 
	 * @param request
	 *            请求数据
	 * @return响应数据
	 */
	@RequestMapping(value = "/wx")
	public String wxPay(HttpServletRequest request, HttpServletResponse response) {
		log.info("get user_ip start ");
		String ipAddr = IpUtil.getIpAddr(request);
		log.info("user ip " + ipAddr);
		// 开始验证ip是否允许
		log.info("check user ip start ");
		boolean checkIpAccess = IpUtil.checkIpAccess(ipAddr);
		log.info("check result " + checkIpAccess + "");
		if (!checkIpAccess) {
			// 验证失败
			JSONObject userResult = new JSONObject();
			userResult.put("errorcode", "23003");
			userResult.put("msg", "您的Ip不合法,服务器拒绝了您的请求");
			log.info("response text " + userResult.toString());
			HttpsUtil.sendAppMessage(userResult.toString(), response);
			return null;
		}
		// 获取请求数据
		String reqText = HttpsUtil.getJsonFromRequest(request);
		log.info("request text pay by wx " + reqText);
		JSONObject resText = payService.getWxPayToken(reqText);
		HttpsUtil.sendAppMessage(resText.toString(), response);
		return null;
	}

	/**
	 * 支付宝支付
	 * 
	 * @param request
	 *            接收数据
	 * @return 响应数据
	 */
	/*@RequestMapping(value = "/ali")
	public String aliPay(HttpServletRequest request, HttpServletResponse response) {
		// 获取请求数据
		String reqText = HttpsUtil.getJsonFromRequest(request);
		log.info("request text pay by ali " + reqText);
		JSONObject resText = payService.getAliPayToken(reqText);
		HttpsUtil.sendAppMessage(resText.toString(), response);
		return null;
	}*/

	/**
	 * 获取微信支付结果通知 notify
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping(value = "/result")
	public String payResult(HttpServletRequest request, HttpServletResponse response) {
		// 获取请求数据
		String reqText = HttpsUtil.getInfoFromRequest(request);
		log.info("request text from wx " + reqText);
		String res2WX = payService.getPayResult(reqText);
		HttpsUtil.sendAppMessage(res2WX, response);
		return null;
	}

	/**
	 * 接收支付宝支付结果通知
	 * @param request
	 *            接收数据
	 * @param response
	 *            响应数据
	 * @return
	 */
	@RequestMapping(value = "/test")
	public String Test(HttpServletRequest request, HttpServletResponse response) {
		// 获取请求数据
		String reqText = HttpsUtil.getInfoFromRequest(request);
		log.info("request text from alipay " + reqText);
		String res2WX = payService.getAliPayResult(reqText);
		HttpsUtil.sendAppMessage(res2WX, response);
		return null;
	}
}
