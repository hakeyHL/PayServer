package cn.com.yunqitong.util;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
/**
* 获取客户端请求的ip  
* 项目名称：AuthorizationServer   
* 类名称：IpUtil   
* 创建人：huli   
* 创建时间：2015-12-22 上午11:52:22      
*
 */
public class IpUtil {
	public static String getIpAddr(HttpServletRequest request) {
		if (null == request) {
			return null;
		}

		String proxs[] = { "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR" };

		String ip = null;

		for (String prox : proxs) {
			ip = request.getHeader(prox);
			if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
				continue;
			} else {
				break;
			}
		}

		if (StringUtils.isBlank(ip)) {
			return request.getRemoteAddr();
		}

		return ip;
	}
	/**
	 * 验证请求ip是否在ip池中
	 * @param userIp 请求者ip
	 * @return 是否允许
	 */
	public static boolean checkIpAccess(String userIp){
		String IPOOL=PropertyFactory.getProperty("IPOOL");
		String []Ips=IPOOL.split("\\|");
		for (String ip : Ips) {
			//遍历ip池,验证是否有匹配ip
			if(ip.equals(userIp)){
				//如果有匹配
				return true;
			}
		}
		//未匹配,返回false
		return false;
	}
}
