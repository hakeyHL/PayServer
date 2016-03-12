package cn.com.yunqitong.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import net.sf.json.JSONObject;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cn.com.yunqitong.domain.TOrderRecord;
import cn.com.yunqitong.domain.TOrderRecordExample;
import cn.com.yunqitong.domain.TWxRecord;
import cn.com.yunqitong.mapper.TOrderRecordMapper;
import cn.com.yunqitong.mapper.TWxRecordMapper;
import cn.com.yunqitong.util.HttpsUtil;
import cn.com.yunqitong.util.IDGenerator;
import cn.com.yunqitong.util.PropertyFactory;
import cn.com.yunqitong.util.SentWxUtil;
import cn.com.yunqitong.util.WxUtil;
import cn.com.yunqitong.util.XMLUtil;

@Service
public class PayService {
	protected Logger log = Logger.getLogger(PayService.class);
	@Autowired
	private TOrderRecordMapper orderMapper;
	@Autowired
	private TWxRecordMapper wxRecordMapper;
	/**
	 * 请求使用微信支付 获取微信支付凭证
	 * @param reqText
	 * @return
	 */
	@SuppressWarnings("all")
	public JSONObject getWxPayToken(String reqText) {
		// 1.确定请求端会传来的数据,做校验和封装
		JSONObject dateFromRemote = JSONObject.fromObject(reqText);
		JSONObject returnCode = new JSONObject();
		String spbill_create_ip = dateFromRemote.optString("spbill_create_ip");
		log.info("ip "+spbill_create_ip);
		String body = dateFromRemote.optString("body");
		log.info("body "+body);
		Long orderMoney = dateFromRemote.optLong("orderMoney");
		log.info("orderMoney "+orderMoney);
		if (spbill_create_ip.equals("")) {
			returnCode.put("errorcode", "70004");
			returnCode.put("msg", "终端地址ip不能为空");
			return returnCode;
		}
		if (body.equals("")) {
			returnCode.put("errorcode", "70004");
			returnCode.put("msg", "商品描述不能为空");
			return returnCode;
		}
		// 整数暂时不校验了 TODO 整数校验

		// body 商品描述 货币类型 total_fee 总金额 spbill_create_ip 终端ip
		String notify_url = PropertyFactory.getProperty("NOTIFYURL");
		log.info("接收支付通知地址  notify_url "+notify_url);
		String appid = PropertyFactory.getProperty("APPID");
		String mch_id = PropertyFactory.getProperty("MCHID");
		String url = PropertyFactory.getProperty("WXADDR");
		String Key = PropertyFactory.getProperty("KEY");
		// notify_url 通知地址 appid 公众账号id mch_id商户号 从配置文件中读取

		// 2.需要动态生成的数据
		// nonce-str 随机字符串 sign 签名 out_trade_no 订单号 trade_type 交易类型--->APP
		String nonce_str = IDGenerator.get32Random() + "";
		String out_trade_no = IDGenerator.get32Random() + "";
		String trade_type = dateFromRemote.optString("tradetype");
		log.info("支付方式  "+trade_type);
		SortedMap<Object, Object> parameters = new TreeMap<Object, Object>();
		parameters.put("appid", appid);
		parameters.put("mch_id", mch_id);
		parameters.put("body", body);
		parameters.put("nonce_str", nonce_str);
		parameters.put("spbill_create_ip", spbill_create_ip+"");
		parameters.put("out_trade_no", out_trade_no);
		parameters.put("trade_type", trade_type);
		
		//添加附加数据,用户公众平台查账使用
		parameters.put("attach", dateFromRemote.optString("accountId"));
		String time_start=WxUtil.getTimeDependWX()+"";
		String time_expire=WxUtil.getTimeDelayDependWX()+"";
		parameters.put("notify_url", notify_url);
		parameters.put("total_fee", orderMoney);
		parameters.put("time_start", time_start);
		parameters.put("time_expire", time_expire);
		//parameters.put("product_id", "115526");
		String sign = WxUtil.createSign("utf-8", parameters, Key);
		log.info("sign "+sign);
		parameters.put("sign", sign);
		TOrderRecord Record = new TOrderRecord();
		/*if(dateFromRemote.optString("adapterid").equals("")){
			//如果为空则为云企通的as
			Record.setAdapterid(1);
		}*/
		//Record.setAdapterid(dateFromRemote.optInt("adapterid"));
		Record.setAttach(dateFromRemote.optString("accountId"));
		Record.setBody(body); 
		Record.setDetail(dateFromRemote.optString("detail")); 
		Record.setDeviceInfo(dateFromRemote.optString("device_info")); 
		Record.setFeeType(dateFromRemote.optString("fee_type")); 
		Record.setGoodsTag(dateFromRemote.optString("goods_tag")); 
		Record.setLimitPay(dateFromRemote.optString("limit_pay"));
		 
		Record.setNonceStr(nonce_str);
		Record.setOid(IDGenerator.getId() + "");
		Record.setOpenid(dateFromRemote.optString("openid"));
		Record.setOutTradeNo(out_trade_no);
		Record.setProductId(dateFromRemote.optString("product_id"));
		Record.setSign(sign);
		Record.setBody(body);
		Record.setSpbillCreateIp(spbill_create_ip);
		Record.setStatus(0);
		Record.setTimeExpire(time_expire);
		Record.setTimeStart(time_start);
		Record.setTotalFee(orderMoney);
		Record.setTradeType(trade_type);
		Record.setAccountid(dateFromRemote.optString("accountId"));
		//如果有,移除不必要的数据,最终保存到acceptcontent 
		dateFromRemote.remove("spbill_create_ip");
		dateFromRemote.remove("body");
		dateFromRemote.remove("device_info");
		//接收客户端传来的消息支付成功后还返回给客户端,所以消息体提前写好
		//将订单号也给AS
		dateFromRemote.put("orderNum", out_trade_no);
		dateFromRemote.put("errorcode", "00000");
		dateFromRemote.put("msg", "支付成功");
		Record.setAcceptContent(dateFromRemote.toString());
		try {
			orderMapper.insertSelective(Record);
		} catch (Exception e) {
			log.error("将支付数据插入库中是异常"+e.getMessage(),e);
			returnCode.put("errorcode", "70072");
			returnCode.put("msg", "支付异常,请稍后重试");
			return returnCode;
		}
		// 3.将数据发送给微信获取和分析包装返回结果之后返回给请求端
		String reqTexts = SentWxUtil.getRquestXmlString(parameters);
		log.info("请求地址为: "+url);
		try {
			log.info("request data 请求数据 "+reqTexts);
			String resText = HttpsUtil.doPost(url, reqTexts);
			log.info("response data 响应结果为 : "+resText);
			//对微信响应结果做解析并返回给AS
			Map resultMap = XMLUtil.doXMLParse(resText);
			//遍历Map
			Set set=resultMap.entrySet();
			Iterator iterator = set.iterator();
			while(iterator.hasNext()){
				Map.Entry entry= (Entry) iterator.next();
				//遍历赋值
				returnCode.put(entry.getKey()+"", entry.getValue()+"");
			}
			if(!returnCode.optString("return_code").equals("SUCCESS")){
				//如果微信返回失败
				returnCode.put("errorcode", returnCode.optString("err_code"));
				returnCode.put("msg", returnCode.optString("err_code_des"));
				return returnCode;
			}
			returnCode.remove("return_code");
			returnCode.remove("return_msg");
			returnCode.remove("result_code");
			if(trade_type.equals("NATIVE")){
				//如果是扫码支付只给链接就行了
				returnCode.put("errorcode", "00000");
				returnCode.put("msg", "成功");
				return returnCode;
			}
			//移除不需要的字段
			returnCode.put("timestamp", WxUtil.getTimeStamp());
			returnCode.put("package", "Sign=WXPay");
			//再次签名
			SortedMap<Object, Object> parameters2 = new TreeMap<Object, Object>();
			parameters2.put("appid", returnCode.optString("appid"));
			parameters2.put("partnerid", returnCode.optString("mch_id"));
			parameters2.put("prepayid", returnCode.optString("prepay_id"));
			parameters2.put("package", returnCode.optString("package"));
			parameters2.put("noncestr", returnCode.optString("nonce_str"));
			parameters2.put("timestamp", returnCode.optString("timestamp"));
			//附加数据  
			parameters2.put("attach", returnCode.optString("attach"));
			String sign2 = WxUtil.createSign("utf-8", parameters2, Key);
			log.info("再次签名 sing again"+sign2);
			returnCode.put("sign", sign2);
			
			returnCode.put("errorcode", "00000");
			returnCode.put("msg", "成功");
		} catch (Exception e) {
			log.error("请求支付异常"+e.getMessage(),e);
			returnCode.put("errorcode", "70072");
			returnCode.put("msg", "请求支付异常,请稍后重试");
			return returnCode;
		}
		return returnCode;
	}
	
	/**
	 * 请求支付宝支付
	 * @param reqText 接收数据
	 * @return    订单数据
	 */
	/*public JSONObject getAliPayToken(String reqText) {
		//接收数据与微信支付接收数据一致,需要新建表,保存请求数据
		log.info("request text from as for ali pay "+reqText);
		JSONObject orderDataForAlipay=JSONObject.fromObject(reqText);
		//1.接收数据,基本校验
		
		//2.获取配置文件中参数
		//以2088开头的合作者身份ID
		String partner=PropertyFactory.getProperty("PARTNER");
		String _input_charset=PropertyFactory.getProperty("INPUTCHARSET");
		String notify_url=PropertyFactory.getProperty("NOTIFYURL_ALI");
		
		//3.生成和组织支付所需信息
		String service="mobile.securitypay.pay";
		//签名方式,目前只支持RSA
		String sign_type="RSA";
		//协商后确定
		String app_id=orderDataForAlipay.optString("app_id");
		String appenv=orderDataForAlipay.optString("appenv");
		//订单号
		String out_trade_no=IDGenerator.getId();
		//商品名称
		String subject=orderDataForAlipay.optString("subject");
		//1 商品购买 
		String payment_type="1";
		//卖家支付宝账号
		String seller_id=PropertyFactory.getProperty("SELLERID");
		//总价
		long total_fee=orderDataForAlipay.optLong("total_fee");
		//商品详情
		String body=orderDataForAlipay.optString("body");
		//商品类型 1.实物交易 0.虚拟交易
		String goods_type="1";
		//是否发起实名校验
		String rn_check="F";
		//未付款交易的超时时间
		String it_b_pay="";
		//授权令牌
		String extern_token="";
		//商户业务拓展参数
		String out_context="";
		
		//4.组织AS向BSS操作所需数据,并将此次相关数据存入本地库中
		orderDataForAlipay.remove("spbill_create_ip");
		orderDataForAlipay.remove("body");
		orderDataForAlipay.remove("device_info");
		
		
		//5.将结果反馈给客户端
		JSONObject j2AS=new JSONObject();
		j2AS.put("", "");
		j2AS.put("", "");
		j2AS.put("", "");
		j2AS.put("", "");
		j2AS.put("", "");
		j2AS.put("", "");
		return j2AS;
	}*/
	/**
	 * 获取支付结果 更新本地记录 并通知AS
	 * 
	 * @param reqText
	 */
	@SuppressWarnings("all")
	public synchronized String getPayResult(String reqText) {
		log.info("微信通知支付结果"+reqText);
		// 1.获取支付结果,
		//JSONObject notify=new JSONObject();
		Map<String,String> map=new HashMap<String, String>();
		JSONObject wxResult=new JSONObject();
		try {
			Map resultMap = XMLUtil.doXMLParse(reqText);
			//遍历Map
			Set set=resultMap.entrySet();
			Iterator iterator = set.iterator();
			while(iterator.hasNext()){
				Map.Entry entry= (Entry) iterator.next();
				//遍历赋值
				wxResult.put(entry.getKey()+"", entry.getValue()+"");
			}
		} catch (Exception e) {
			log.error("转换微信通知结果时异常"+e.getMessage(),e);
		}
		//TODO 对微信返回信息生成签名与微信返回签名比对,验证消息来源
		TOrderRecord tOrderRecord=null;
		// 2. 失败,告知微信已收到消息
		if(!wxResult.optString("return_code").equals("SUCCESS")){
			//请求关闭订单 需要:appid mch_id out_trade_no nonce_str sign
			//String url=PropertyFactory.getProperty("WXCLOSEORDER");
			log.info("微信通知支付失败,流程结束 ");
			map.put("return_code", "SUCCESS");
			map.put("return_msg", "OK");
			String rquestXmlString = SentWxUtil.getRquestXmlString(map);
			log.info("返回给微信的信息 "+rquestXmlString);
			return rquestXmlString;
		}
			//否则,校验当前订单是否已经完成,获取当前订单号的状态
			String orderNum = wxResult.optString("out_trade_no");
			TOrderRecordExample example2=new TOrderRecordExample();
			example2.createCriteria().andOutTradeNoEqualTo(orderNum);
			List<TOrderRecord> selectByExample = orderMapper.selectByExample(example2);
			tOrderRecord= selectByExample.get(0);
			Integer status = tOrderRecord.getStatus();
			if(status==1){
				//已完成,通知微信不要再通知了
				log.info("订单已经完成,通知微信交易结束,已收到消息");
				map.put("return_code", "SUCCESS");
				map.put("return_msg", "OK");
				String rquestXmlString = SentWxUtil.getRquestXmlString(map);
				log.info("返回给微信的信息 "+rquestXmlString);
				return rquestXmlString;
			}
		log.info("微信返回结果 json  "+wxResult);
		// 3.无误后更新本地记录
		log.info("将返回信息插入本地数据库中...");
		TWxRecord wxRecord=new TWxRecord();
		try {
			wxRecord.setAttach(wxResult.optString("attach"));
			wxRecord.setBankType(wxResult.optString("bank_type"));
			wxRecord.setCashFee(0);
			wxRecord.setCashFeeType(wxResult.optString("cash_fee_type"));
			wxRecord.setCouponCount(0);
			wxRecord.setCouponFee(wxResult.optInt("coupon_fee"));
			wxRecord.setCouponFeeN(0);
			wxRecord.setCouponIdN("yqt");
			wxRecord.setDeviceInfo(wxResult.optString("device_info"));
			wxRecord.setErrCode(wxResult.optString("err_code"));
			wxRecord.setErrCodeMsg(wxResult.optString("err_code_des"));
			wxRecord.setFeeType(wxResult.optString("fee_type"));
			wxRecord.setIsSubscribe(wxResult.optString("is_subscribe"));
			wxRecord.setNonceStr(wxResult.optString("nonce_str"));
			wxRecord.setOpenid(wxResult.optString("openid"));
			wxRecord.setOutTradeNo(wxResult.optString("out_trade_no"));
			wxRecord.setResultCode(wxResult.optString("result_code"));
			wxRecord.setReturnMsg(wxResult.optString("return_msg"));
			wxRecord.setSign(wxResult.optString("sign"));
			wxRecord.setTimeEnd(wxResult.optString("time_end"));
			wxRecord.setTotalFee(wxResult.optInt("total_fee"));
			wxRecord.setTradeType(wxResult.optString("trade_type"));
			wxRecord.setTransactionId(wxResult.optString("transaction_id"));
			wxRecord.setUpdatetime(new Date().toLocaleString());
			wxRecord.setId(IDGenerator.getId());
			wxRecordMapper.insertSelective(wxRecord);
			log.info("更新订单状态为已支付 ");
			tOrderRecord.setStatus(1);
			orderMapper.updateByPrimaryKey(tOrderRecord);
		} catch (Exception e) {
			log.error("将支付通知插入到数据库时异常"+e.getMessage(),e);
		}
		log.info("插入完成,通知AS支付结果并将订购信息给AS");
		// 4.通知AS支付结果
		//String url=null;
	/*	if(tOrderRecord.getAdapterid()==2){
			//如果是恋家adapter则通知地址修改为恋家adapter地址
			url=PropertyFactory.getProperty("LJASSADDR");
		}else{
			//否则为云企通
			url=PropertyFactory.getProperty("ASSADDR");
		}*/
		String url=PropertyFactory.getProperty("ASSADDR");
		try {
			HttpsUtil.doPost(url+"/mobile/pay/wx", tOrderRecord.getAcceptContent());
		} catch (Exception e) {
			log.error("通知AS支付结果时异常"+e.getMessage(),e);
		}
		log.info("所有操作已完毕,通知微信已接收到通知,交易结束...");
		//都没有问题,返回微信已接收支付成功结果
		map.put("return_code", "SUCCESS");
		map.put("return_msg", "OK");
		String rquestXmlString = SentWxUtil.getRquestXmlString(map);
		log.info("返回给微信的信息 "+rquestXmlString);
		return rquestXmlString;

	}
	
	/**
	 * 获取支付宝支付结果通知
	 * @param reqText 请求数据
	 * @return 响应数据
	 * 加锁 :防止函数重入导致业务数据混乱
	 */
	public synchronized  String getAliPayResult(String reqText) {
		//1.验证签名是否正确,对除sign和sign_type之外的其他数据+key生成签名
		//2.验证通知是否为支付宝通知
		//3.检查业务状态,防止重复通知引起的资金流失,已成功则返回成功
		//4.将支付宝返回数据插入本地
		//5.更新订单状态
		//6.通知AS支付结果,并将订单数据交予AS
		//7.告知支付宝操作结果
		return null;
	}

}
