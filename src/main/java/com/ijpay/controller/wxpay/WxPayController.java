package com.ijpay.controller.wxpay;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ijpay.entity.*;
import com.ijpay.service.OrderService;
import com.ijpay.utils.GetString;
import com.ijpay.utils.HttpRequest;
import com.ijpay.utils.MD5;
import com.ijpay.utils.WXUtil;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.wechat.config.WechatMpProperties;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.ibatis.annotations.Param;
import org.jdom2.JDOMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.alibaba.fastjson.JSON;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.jpay.ext.kit.HttpKit;
import com.jpay.ext.kit.IpKit;
import com.jpay.ext.kit.PaymentKit;
import com.jpay.ext.kit.StrKit;
import com.jpay.ext.kit.ZxingKit;
import com.jpay.vo.AjaxResult;
import com.jpay.weixin.api.WxPayApi;
import com.jpay.weixin.api.WxPayApi.TradeType;
import com.jpay.weixin.api.WxPayApiConfig;
import com.jpay.weixin.api.WxPayApiConfig.PayModel;
import com.jpay.weixin.api.WxPayApiConfigKit;


@Controller
@RequestMapping("/wxpay")
public class WxPayController extends WxPayApiController {
	private Logger log = LoggerFactory.getLogger(this.getClass());
	private AjaxResult result = new AjaxResult();

	@Autowired
	WxPayBean wxPayBean;

	@Autowired
			private OrderService orderService;
	
	String notify_url;

	@Override
	public WxPayApiConfig getApiConfig() {
		notify_url = wxPayBean.getDomain().concat("/wxpay/pay_result");
		return WxPayApiConfig.New()
				.setAppId(wxPayBean.getAppId())
				.setMchId(wxPayBean.getMchId())
				.setPaternerKey(wxPayBean.getPartnerKey())
				.setPayModel(PayModel.BUSINESSMODEL);
	}

	@RequestMapping("")
	@ResponseBody
	public String index() {
		log.info("欢迎使用IJPay,商户模式下微信支付 - by Javen");
		log.info(String.valueOf(WxPayBean.class));
		return ("欢迎使用IJPay 商户模式下微信支付  - by Javen");
	}
	
	@RequestMapping("/test")
	@ResponseBody
	public String test(){
		return String.valueOf(WxPayBean.class);
	}
	
	@RequestMapping("/testWeChat")
	@ResponseBody
	public String testWeChat(){
		return String.valueOf(WechatMpProperties.class);
	}
	
	@RequestMapping("/getKey")
	@ResponseBody
	public String getKey(){
		return WxPayApi.getsignkey(wxPayBean.getAppId(), wxPayBean.getPartnerKey());
	}
	@RequestMapping("/ctp")
	@ResponseBody
	public String ctp(HttpServletRequest request){
		String dir = request.getServletContext().getRealPath("/");
		return dir;
	}


	//微信支付控制器

	@Override
	public WxPayApiConfig getWxApiConfig() {
		return WxPayApiConfig.New()
				.setAppId("wx172657dad29220cc")//wx172657dad29220cc
				.setMchId(wxPayBean.getMchId())
				.setPaternerKey(wxPayBean.getPartnerKey())
				.setPayModel(PayModel.BUSINESSMODEL);
	}


	/**小程序支付下单
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/jxAppPay",method={RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public JSONObject jxAppPay(@Param("openid") String openid, @Param("fee") Float fee, @Param("outTradeNo")String outTradeNo, HttpServletRequest request){
		log.info("---------------------微信支付下单------------------------------");
		System.out.println("小程序 appID："+getWxApiConfig().getAppId());
		System.out.println("app appID："+WxPayApiConfigKit.getAppId());
		String ip = IpKit.getRealIp(request);
		if (StrKit.isBlank(ip)) {
			ip = "127.0.0.1";
		}
		log.info("回调地址："+notify_url);
		Map<String, String> params = new HashMap<>();
		params.put("appid",wxPayBean.getAppID());
		params.put("mch_id", wxPayBean.getMch_id());
		params.put("nonce_str", String.valueOf(System.currentTimeMillis()));
		params.put("body", "小程序测试 by arison");
		if (outTradeNo==null){
			System.out.println("++++++++++++++++");
			String s = System.currentTimeMillis() + "";
			params.put("out_trade_no", s);
			orderService.addOrderBy(String.valueOf(System.currentTimeMillis()),"","微信小程序",openid,s,fee,params.get("body"),"","","JSAPI",new Date(),"0","");
		}else {
			params.put("out_trade_no",outTradeNo);
		}

		params.put("total_fee",String.valueOf((int)(fee*100)));
		params.put("notify_url", "http://nf20718343.iask.in:15161/wxpay/pay_result");
		params.put("spbill_create_ip",ip);
		//params.put("sign_type", "MD5");//非必填
		params.put("trade_type", "JSAPI");
		params.put("openid", openid);
		params.put("sign",PaymentKit.createSign( params, wxPayBean.getKey()));

		log.info("params:"+JSON.toJSONString(params));
		//支付
		String xmlResult =  WxPayApi.pushOrder(false,params);

		// log.info(xmlResult);
		Map<String, String> resultMap = PaymentKit.xmlToMap(xmlResult);

		String return_code = resultMap.get("return_code");
		String return_msg = resultMap.get("return_msg");
		if (!PaymentKit.codeIsOK(return_code)) {
			//log.info(xmlResult);
			//result.addError(return_msg);
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("success",false);
			jsonObject.put("msg",return_msg);
			return jsonObject;
		}
		String result_code = resultMap.get("result_code");
		if (!PaymentKit.codeIsOK(result_code)) {
			log.info(xmlResult);
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("success",false);
			jsonObject.put("msg",return_msg);
			//result.addError(return_msg);
			return jsonObject;
		}
		// 以下字段在return_code 和result_code都为SUCCESS的时候有返回

		String prepay_id = resultMap.get("prepay_id");
		//小程序调起支付数据签名字段列表：https://pay.weixin.qq.com/wiki/doc/api/wxa/wxa_api.php?chapter=7_7&index=5
		//签名字段要严格按照文档的字段，大小写一定要根据文档字段一致
		Map<String, String> packageParams = new HashMap<String, String>();
		packageParams.put("appId", getWxApiConfig().getAppId());
		packageParams.put("signType", "MD5");//关键字段
		packageParams.put("package", "prepay_id="+prepay_id);//Sign=WXPay
		packageParams.put("nonceStr", System.currentTimeMillis() + "");
		packageParams.put("timeStamp", System.currentTimeMillis() / 1000 + "");
		String packageSign = PaymentKit.createSign(packageParams, WxPayApiConfigKit.getWxPayApiConfig().getPaternerKey());
		packageParams.put("sign", packageSign);
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("success",true);
		jsonObject.put("data",packageParams);
		//result.success(jsonObject);
		return jsonObject;
	}

	/**
	 * 小程序支付
	 */
	@RequestMapping(value = "/getOpenid",method = {RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public JSONObject getOpenid(String code) throws IOException {
		HttpGet httpGet = new HttpGet("https://api.weixin.qq.com/sns/jscode2session?appid="+ wxPayBean.getAppID()+"&secret="+wxPayBean.getSecret()+"&js_code="+code+"&grant_type=authorization_code");
		//设置请求器的配置
		HttpClient httpClient = HttpClients.createDefault();
		HttpResponse res = httpClient.execute(httpGet);
		HttpEntity entity = res.getEntity();
		String result = EntityUtils.toString(entity, "UTF-8");
		JSONObject reqjson = JSONObject.parseObject(result);
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("success", true);
		jsonObject.put("data", reqjson);
		return jsonObject;
	}


	@RequestMapping(value = "/pay",method = {RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public JSONObject pay(@Param("openid") String openid, @Param("fee") Float fee,@Param("outTradeNo")String outTradeNo){
		System.out.println(openid+"====================");
		System.out.println(fee+"++++++++++++++++++++");
		try {
			OrderInfo order = new OrderInfo();
			order.setAppid(wxPayBean.getAppID());
			System.out.println("==========="+wxPayBean.getAppID());
			order.setMch_id(wxPayBean.getMch_id());
			order.setNonce_str(System.currentTimeMillis() + "");
			order.setBody("test");
			if (outTradeNo==null){
				System.out.println("++++++++++++++++");
				String s = System.currentTimeMillis() + "";
				order.setOut_trade_no(s);
				orderService.addOrderBy("","","微信小程序",openid,s,fee,"test","","","JSAPI",new Date(),"0","");
			}else {
				order.setOut_trade_no(outTradeNo);
			}
			order.setTotal_fee((int) (fee * 100));
			//order.setTotal_fee(1);
			order.setSpbill_create_ip("192.168.253.230");
			order.setNotify_url(notify_url);
			order.setTrade_type("JSAPI");
			order.setOpenid(openid);
			order.setSign_type("MD5");
			//生成签名
			//String sign = Signature.getSign(order);
			ArrayList<String> list = new ArrayList<String>();
			Class cls = order.getClass();
			Field[] fields = cls.getDeclaredFields();
			for (Field f : fields) {
				f.setAccessible(true);
				if (f.get(order) != null && f.get(order) != "") {
					String name = f.getName();
					XStreamAlias anno = f.getAnnotation(XStreamAlias.class);
					if(anno != null)
						name = anno.value();
					list.add(name + "=" + f.get(order) + "&");
				}
			}
			int size = list.size();
			String [] arrayToSort = list.toArray(new String[size]);
			Arrays.sort(arrayToSort, String.CASE_INSENSITIVE_ORDER);
			StringBuilder sb = new StringBuilder();
			for(int i = 0; i < size; i ++) {
				sb.append(arrayToSort[i]);
			}
			String res = sb.toString();
			res += "key=" + wxPayBean.getKey();
			System.out.println("签名数据："+res);
			String sign = MD5.MD5Encode(res,"utf-8").toUpperCase();
			order.setSign(sign);
			String result = HttpRequest.sendPost("https://api.mch.weixin.qq.com/pay/unifiedorder", order);
			System.out.println(result);
			XStream xStream = new XStream(new DomDriver());
			xStream.alias("xml", OrderReturnInfo.class);
			OrderReturnInfo Info = (OrderReturnInfo) xStream.fromXML(result);
			System.out.println(Info + "++++++++++++++++++++++++++++");
			JSONObject json = new JSONObject();
			json.put("prepay_id", Info.getPrepay_id());
			return json;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@RequestMapping(value = "/getSign",method = {RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public JSONObject getSign(String prepay_id){
		try {

			SignInfo signInfo = new SignInfo();
			signInfo.setAppId(wxPayBean.getAppID());
			long time = System.currentTimeMillis()/1000;
			signInfo.setTimeStamp(String.valueOf(time));
			signInfo.setNonceStr(GetString.getRandomStringByLength(32));
			signInfo.setRepay_id("prepay_id="+prepay_id);
			signInfo.setSignType("MD5");
			//生成签名
			//String sign = Signature.getSign(signInfo);
			ArrayList<String> list = new ArrayList<String>();
			Class cls = signInfo.getClass();
			Field[] fields = cls.getDeclaredFields();
			for (Field f : fields) {
				f.setAccessible(true);
				if (f.get(signInfo) != null && f.get(signInfo) != "") {
					String name = f.getName();
					XStreamAlias anno = f.getAnnotation(XStreamAlias.class);
					if(anno != null)
						name = anno.value();
					list.add(name + "=" + f.get(signInfo) + "&");
				}
			}
			int size = list.size();
			String [] arrayToSort = list.toArray(new String[size]);
			Arrays.sort(arrayToSort, String.CASE_INSENSITIVE_ORDER);
			StringBuilder sb = new StringBuilder();
			for(int i = 0; i < size; i ++) {
				sb.append(arrayToSort[i]);
			}
			String result = sb.toString();
			result += "key=" + wxPayBean.getKey();
			System.out.println("签名数据："+result);
			String sign = MD5.MD5Encode(result,"utf-8").toUpperCase();

			JSONObject json = new JSONObject();
			json.put("timeStamp", signInfo.getTimeStamp());
			json.put("nonceStr", signInfo.getNonceStr());
			json.put("package", signInfo.getRepay_id());
			json.put("signType", signInfo.getSignType());
			json.put("paySign", sign);
			return json;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}
	/**
	 * 微信H5 支付
	 * 注意：必须再web页面中发起支付且域名已添加到开发配置中
	 */
	@RequestMapping(value ="/wapPay",method = {RequestMethod.POST,RequestMethod.GET})
	public void wapPay(HttpServletRequest request,HttpServletResponse response){
		String ip = IpKit.getRealIp(request);
		if (StrKit.isBlank(ip)) {
			ip = "127.0.0.1";
		}
		
		H5ScencInfo sceneInfo = new H5ScencInfo();
		
		H5ScencInfo.H5 h5_info = new H5ScencInfo.H5();
		h5_info.setType("Wap");
		//此域名必须在商户平台--"产品中心"--"开发配置"中添加

		h5_info.setWap_url("https://pay.qq.com");
		h5_info.setWap_name("腾讯充值");
		sceneInfo.setH5_info(h5_info);
		
		Map<String, String> params = WxPayApiConfigKit.getWxPayApiConfig()
				.setAttach("IJPay H5支付测试  -By Javen")
				

				.setBody("IJPay H5支付测试  -By Javen")
				.setSpbillCreateIp(ip)
				.setTotalFee("520")
				.setTradeType(TradeType.MWEB)
				.setNotifyUrl(notify_url)
				.setOutTradeNo(String.valueOf(System.currentTimeMillis()))
				.setSceneInfo(h5_info.toString())
				.build();
		
		String xmlResult = WxPayApi.pushOrder(false,params);
log.info(xmlResult);
		Map<String, String> result = PaymentKit.xmlToMap(xmlResult);
		
		String return_code = result.get("return_code");
		String return_msg = result.get("return_msg");
		if (!PaymentKit.codeIsOK(return_code)) {
			log.error("return_code>"+return_code+" return_msg>"+return_msg);
			throw new RuntimeException(return_msg);
		}
		String result_code = result.get("result_code");
		if (!PaymentKit.codeIsOK(result_code)) {
			log.error("result_code>"+result_code+" return_msg>"+return_msg);
			throw new RuntimeException(return_msg);
		}
		// 以下字段在return_code 和result_code都为SUCCESS的时候有返回
		
		String prepay_id = result.get("prepay_id");
		String mweb_url = result.get("mweb_url");
		
		log.info("prepay_id:"+prepay_id+" mweb_url:"+mweb_url);
		try {
			response.sendRedirect(mweb_url);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * 公众号支付
	 */
	@RequestMapping(value ="/webPay",method = {RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public AjaxResult webPay(HttpServletRequest request,HttpServletResponse response,
			@RequestParam("total_fee") String total_fee) {
		// openId，采用 网页授权获取 access_token API：SnsAccessTokenApi获取
		String openId = (String) request.getSession().getAttribute("openId");
		
		if (StrKit.isBlank(openId)) {
			result.addError("openId is null");
			return result;
		}
		if (StrKit.isBlank(total_fee)) {
			result.addError("请输入数字金额");
			return result;
		}
		
		String ip = IpKit.getRealIp(request);
		if (StrKit.isBlank(ip)) {
			ip = "127.0.0.1";
		}
		
		Map<String, String> params = WxPayApiConfigKit.getWxPayApiConfig()
				.setAttach("IJPay 公众号支付测试  -By Javen")
				.setBody("IJPay 公众号支付测试  -By Javen")
				.setOpenId(openId)
				.setSpbillCreateIp(ip)
				.setTotalFee(total_fee)
				.setTradeType(TradeType.JSAPI)
				.setNotifyUrl(notify_url)
				.setOutTradeNo(String.valueOf(System.currentTimeMillis()))
				.build();
		
		String xmlResult = WxPayApi.pushOrder(false,params);
log.info(xmlResult);
		Map<String, String> resultMap = PaymentKit.xmlToMap(xmlResult);
		
		String return_code = resultMap.get("return_code");
		String return_msg = resultMap.get("return_msg");
		if (!PaymentKit.codeIsOK(return_code)) {
			result.addError(return_msg);
			return result;
		}
		String result_code = resultMap.get("result_code");
		if (!PaymentKit.codeIsOK(result_code)) {
			result.addError(return_msg);
			return result;
		}
		// 以下字段在return_code 和result_code都为SUCCESS的时候有返回

		String prepay_id = resultMap.get("prepay_id");
		
		Map<String, String> packageParams = PaymentKit.prepayIdCreateSign(prepay_id);
		
		String jsonStr = JSON.toJSONString(packageParams);
		result.success(jsonStr);
		return result;
	}
	
	/**
	 * 生成支付二维码（模式一）并在页面上显示
	 */
	@RequestMapping(value ="/scanCode1",method ={RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public AjaxResult scanCode1(HttpServletRequest request,HttpServletResponse response,
			@RequestParam("productId") String product_id){
		try {
			if (StrKit.isBlank(product_id)) {
				result.addError("productId is null");
				return result;
			}
			WxPayApiConfig config = WxPayApiConfigKit.getWxPayApiConfig();
			//获取扫码支付（模式一）url

			String qrCodeUrl=WxPayApi.getCodeUrl(config.getAppId(), config.getMchId(),product_id, config.getPaternerKey(), true);
			log.info(qrCodeUrl);
			//生成二维码保存的路径

			String name = "payQRCode1.png";
			System.out.println(request.getServletContext().getRealPath("/")+File.separator+name);
			Boolean encode = ZxingKit.encode(qrCodeUrl, BarcodeFormat.QR_CODE, 3, ErrorCorrectionLevel.H, "png", 200, 200,
					request.getServletContext().getRealPath("/")+File.separator+name );
			if (encode) {
				//在页面上显示
				result.success(name);
				return result;
			}
		} catch (Exception e) {
			e.printStackTrace();
			result.addError("系统异常："+e.getMessage());
			return result;
		}
		
		return null;
	}
	
	
	/**
	 * 扫码支付模式一回调
	 * 已测试
	 */
	@RequestMapping(value ="/wxpay",method = {RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public String wxpay(HttpServletRequest request,HttpServletResponse response){
		try {
		
			String result  = HttpKit.readData(request);
			System.out.println("callBack_xml>>>"+result);
			/**

			 * 获取返回的信息内容中各个参数的值

			 */
			Map<String, String> map = PaymentKit.xmlToMap(result);
			for (String key : map.keySet()) {
				   System.out.println("key= "+ key + " and value= " + map.get(key));
			}
			
			String appid=map.get("appid");
			String openid = map.get("openid");
			String mch_id = map.get("mch_id");
			String is_subscribe = map.get("is_subscribe");
			String nonce_str = map.get("nonce_str");
			String product_id = map.get("product_id");
			String sign = map.get("sign");
			Map<String, String> packageParams = new HashMap<String, String>();
			packageParams.put("appid", appid);
			packageParams.put("openid", openid);
			packageParams.put("mch_id",mch_id);
			packageParams.put("is_subscribe",is_subscribe);
			packageParams.put("nonce_str",nonce_str);
			packageParams.put("product_id", product_id);
			
			String packageSign = PaymentKit.createSign(packageParams, WxPayApiConfigKit.getWxPayApiConfig().getPaternerKey());
			// 统一下单文档地址：https://pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=9_1


			
			String ip = IpKit.getRealIp(request);
			if (StrKit.isBlank(ip)) {
				ip = "127.0.0.1";
			}
			
			Map<String, String> params = WxPayApiConfigKit.getWxPayApiConfig()
					.setAttach("IJPay 扫码模式一测试  -By Javen")
					.setBody("IJPay 扫码模式一测试  -By Javen")
					.setOpenId(openid)
					.setSpbillCreateIp(ip)
					.setTotalFee("100")
					.setTradeType(TradeType.NATIVE)
					.setNotifyUrl(notify_url)
					.setOutTradeNo(String.valueOf(System.currentTimeMillis()))
					.build();
			
			String xmlResult = WxPayApi.pushOrder(false,params);
			log.info("prepay_xml>>>"+xmlResult);
			
			/**

	         * 发送信息给微信服务器

	         */
			Map<String, String> payResult = PaymentKit.xmlToMap(xmlResult);
			
			String return_code = payResult.get("return_code");
			String result_code = payResult.get("result_code");
			
			if (StrKit.notBlank(return_code) && StrKit.notBlank(result_code) && return_code.equalsIgnoreCase("SUCCESS")&&result_code.equalsIgnoreCase("SUCCESS")) {
				// 以下字段在return_code 和result_code都为SUCCESS的时候有返回

				String prepay_id = payResult.get("prepay_id");
				
				Map<String, String> prepayParams = new HashMap<String, String>();
				prepayParams.put("return_code", "SUCCESS");
				prepayParams.put("appId", appid);
				prepayParams.put("mch_id", mch_id);
				prepayParams.put("nonceStr", System.currentTimeMillis() + "");
				prepayParams.put("prepay_id", prepay_id);
				String prepaySign = null;
				if (sign.equals(packageSign)) {
					prepayParams.put("result_code", "SUCCESS");
				}else {
					prepayParams.put("result_code", "FAIL");
					prepayParams.put("err_code_des", "订单失效");   //result_code为FAIL时，添加该键值对，value值是微信告诉客户的信息

				}
				prepaySign = PaymentKit.createSign(prepayParams, WxPayApiConfigKit.getWxPayApiConfig().getPaternerKey());
				prepayParams.put("sign", prepaySign);
				String xml = PaymentKit.toXml(prepayParams);
				log.error(xml);
				return xml;
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * 扫码支付模式二
	 * 已测试
	 */
	@RequestMapping(value ="/scanCode2",method = {RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public AjaxResult scanCode2(HttpServletRequest request,HttpServletResponse response,
			@RequestParam("total_fee") String total_fee) {
		
//		String openId="o5NJx1dVRilQI6uUVSaBDuLnM3iM";

		String openId = (String) request.getSession().getAttribute("openId");
		
		
		if (StrKit.isBlank(openId)) {
			result.addError("openId is null");
			return result;
		}
		if (StrKit.isBlank(total_fee)) {
			result.addError("支付金额不能为空");
			return result;
		}
		
		String ip = IpKit.getRealIp(request);
		if (StrKit.isBlank(ip)) {
			ip = "127.0.0.1";
		}
		
		Map<String, String> params = WxPayApiConfigKit.getWxPayApiConfig()
				.setAttach("IJPay 测试  -By Javen")
				.setBody("IJPay 扫码支付2测试  -By Javen")
				.setOpenId(openId)
				.setSpbillCreateIp(ip)
				.setTotalFee(total_fee)
				.setTradeType(TradeType.NATIVE)
				.setNotifyUrl(notify_url)
				.setOutTradeNo(String.valueOf(System.currentTimeMillis()))
				.build();
		
		String xmlResult = WxPayApi.pushOrder(false,params);
		
log.info(xmlResult);
		Map<String, String> resultMap = PaymentKit.xmlToMap(xmlResult);
		
		String return_code = resultMap.get("return_code");
		String return_msg = resultMap.get("return_msg");
		if (!PaymentKit.codeIsOK(return_code)) {
			System.out.println(xmlResult);
			result.addError("error:"+return_msg);
			return result;
		}
		String result_code = resultMap.get("result_code");
		if (!PaymentKit.codeIsOK(result_code)) {
			System.out.println(xmlResult);
			result.addError("error:"+return_msg);
			return result;
		}
		//生成预付订单success

		
		String qrCodeUrl = resultMap.get("code_url");
		String name = "payQRCode2.png";
		
		Boolean encode = ZxingKit.encode(qrCodeUrl, BarcodeFormat.QR_CODE, 3, ErrorCorrectionLevel.H, "png", 200, 200,
				request.getServletContext().getRealPath("/")+File.separator+name);
		if (encode) {
//			renderQrCode(qrCodeUrl, 200, 200);

			//在页面上显示

			result.success(name);
			return result;
		}
		return null;
	}
	
	
	/**
	 * 刷卡支付
	 * 已测试
	 */
	@RequestMapping(value = "/micropay",method= {RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public AjaxResult micropay(HttpServletRequest request,HttpServletResponse response){
		String auth_code = request.getParameter("auth_code");
		String total_fee = request.getParameter("total_fee");
		
		if (StrKit.isBlank(total_fee)) {
			result.addError("支付金额不能为空");
			return result;
		}
		if (StrKit.isBlank(auth_code)) {
			result.addError("auth_code参数错误");
			return result;
		}
		
		String ip = IpKit.getRealIp(request);
		if (StrKit.isBlank(ip)) {
			ip = "127.0.0.1";
		}
		
		Map<String, String> params = WxPayApiConfigKit.getWxPayApiConfig()
				.setAttach("IJPay 测试  -By Javen")
				.setBody("IJPay 刷卡支付测试 -By Javen")
				.setSpbillCreateIp(ip)
				.setTotalFee(total_fee)
				.setAuthCode(auth_code)
				.setTradeType(TradeType.MICROPAY)
				.setNotifyUrl(notify_url)
				.setOutTradeNo(String.valueOf(System.currentTimeMillis()))
				.build();
				
		String xmlResult =  WxPayApi.micropay(false,params);
		
		//同步返回结果

		log.info("xmlResult:"+xmlResult);
		
		Map<String, String> resultMap = PaymentKit.xmlToMap(xmlResult);
		String return_code = resultMap.get("return_code");
		String return_msg = resultMap.get("return_msg");
		if (!PaymentKit.codeIsOK(return_code)) {
			//通讯失败 

			String err_code = resultMap.get("err_code");
			if (StrKit.notBlank(err_code)) {
				//用户支付中，需要输入密码

				if (err_code.equals("USERPAYING")) {
					//等待5秒后调用【查询订单API】https://pay.weixin.qq.com/wiki/doc/api/micropay.php?chapter=9_2

					
				}
			}
			log.info("提交刷卡支付失败>>"+xmlResult);
			result.addError(return_msg);
			return result;
		}
		
		String result_code = resultMap.get("result_code");
		if (!PaymentKit.codeIsOK(result_code)) {
			//支付失败
			log.info("支付失败>>"+xmlResult);
			String err_code_des = resultMap.get("err_code_des");
			
			result.addError(err_code_des);
			return result;
		}
		//支付成功 
		result.success(xmlResult);
		return result;
	}
	
	/**
	 * 微信APP支付
	 */
	@RequestMapping(value = "/appPay",method={RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public JSONObject appPay(@Param("fee") Float fee, @Param("userid")String userid, @Param("out_trade_no")String out_trade_no, HttpServletRequest request){
		log.info("---------------------微信支付下单------------------------------");
		//不用设置授权目录域名
		//统一下单地址 https://pay.weixin.qq.com/wiki/doc/api/app/app.php?chapter=9_1#
		
		String ip = IpKit.getRealIp(request);
		if (StrKit.isBlank(ip)) {
			ip = "127.0.0.1";
		}
		log.info("回调地址："+notify_url);
		String s;
		if (out_trade_no==null){
			s=String.valueOf(System.currentTimeMillis());
		}else {
			s=out_trade_no;
		}
		Map<String, String> params = WxPayApiConfigKit.getWxPayApiConfig()
				.setAttach("By Javen")
				.setBody("By Javen")
				.setSpbillCreateIp("192.168.253.230")
				.setTotalFee(String.valueOf((int)(fee*100)))
				.setTradeType(TradeType.APP)
				.setNotifyUrl(notify_url)
				.setOutTradeNo(s)
				.build();
			log.info("params:"+JSON.toJSONString(params)	);
			//支付
		String xmlResult =  WxPayApi.pushOrder(false,params);
		
        // log.info(xmlResult);
		Map<String, String> resultMap = PaymentKit.xmlToMap(xmlResult);
		
		String return_code = resultMap.get("return_code");
		String return_msg = resultMap.get("return_msg");
		JSONObject jsonObject = new JSONObject();
		if (!PaymentKit.codeIsOK(return_code)) {
			log.info(xmlResult);
			//result.addError(return_msg);
			jsonObject.put("success",false);
			jsonObject.put("msg",return_msg);
			return jsonObject;
		}
		String result_code = resultMap.get("result_code");
		if (!PaymentKit.codeIsOK(result_code)) {
			log.info(xmlResult);
			//result.addError(return_msg);
			jsonObject.put("success",false);
			jsonObject.put("msg",return_msg);
			return jsonObject;
			//return result;
		}
		// 以下字段在return_code 和result_code都为SUCCESS的时候有返回

		String prepay_id = resultMap.get("prepay_id");
		//封装调起微信支付的参数 https://pay.weixin.qq.com/wiki/doc/api/app/app.php?chapter=9_12

		Map<String, String> packageParams = new HashMap<String, String>();
		packageParams.put("appid", WxPayApiConfigKit.getWxPayApiConfig().getAppId());
		packageParams.put("partnerid", WxPayApiConfigKit.getWxPayApiConfig().getMchId());
		packageParams.put("prepayid", prepay_id);
		packageParams.put("package", "Sign=WXPay");
		packageParams.put("noncestr", System.currentTimeMillis() + "");
		packageParams.put("timestamp", System.currentTimeMillis() / 1000 + "");
		String packageSign = PaymentKit.createSign(packageParams, WxPayApiConfigKit.getWxPayApiConfig().getPaternerKey());
		packageParams.put("sign", packageSign);
		orderService.addOrderBy("","","10","",s,fee,params.get("body"),"","","APP",new Date(),"0",userid);
		
		/*String jsonStr = JSON.toJSONString(packageParams);
		log.info("最新返回apk的参数:"+jsonStr);
		result.success(jsonStr);
		return result;*/
		jsonObject.put("success",true);
		jsonObject.put("data",packageParams);
		return jsonObject;
	}
	
	
	
	/**
	 * 退款
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/appRefund",method={RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public JSONObject orderRefund(@Param("fee")Float fee, @Param("refund_fee")Float refund_fee, @Param("transaction_id")String transaction_id, @Param("outTradeNo")String outTradeNo, HttpServletRequest request){
		System.out.println("----------------退款--------------------------");
		String certPath=request.getServletContext().getRealPath("/")+"WEB-INF"+"\\apiclient_cert.p12";
		String certPass=WxPayApiConfigKit.getWxPayApiConfig().getMchId();
		System.out.println("证书路径："+certPath);
		System.out.println("证书密码："+certPass);
		WxPayApiConfigKit.getWxPayApiConfig().setContractNotifyUrl("http://nf20718343.iask.in:15161/wxpay/refund_result");
		//调用证书
		Map<String, String> params =new HashMap<>();	
		params.put("appid", WxPayApiConfigKit.getWxPayApiConfig().getAppId());
		params.put("mch_id",WxPayApiConfigKit.getWxPayApiConfig().getMchId());
		params.put("nonce_str",System.currentTimeMillis() + "");//随机字符串-随机数
		String s = System.currentTimeMillis() + "";
		params.put("out_refund_no",s);//商户退款单号 -随机数
		params.put("out_trade_no", outTradeNo);//商户订单号  二选一
		params.put("transaction_id",transaction_id);//微信订单号 二选一
		System.out.println(refund_fee);
		System.out.println(fee);
		System.out.println(transaction_id);
		System.out.println(outTradeNo);
		params.put("refund_fee",String.valueOf((int)(refund_fee*100)));
		params.put("total_fee", String.valueOf((int)(fee*100)));
		params.put("notify_url", WxPayApiConfigKit.getWxPayApiConfig().getContractNotifyUrl());
		params.put("sign", PaymentKit.createSign(params, WxPayApiConfigKit.getWxPayApiConfig().getPaternerKey()));
		orderService.modifyOrderById(outTradeNo,transaction_id,s,refund_fee,"2",new Date());
		System.out.println("微信退款参数："+JSON.toJSONString(params));

		
	    String info= WxPayApi.orderRefund(false, params, certPath, certPass);
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("success",true);
		jsonObject.put("data",info);
		//result.setData(info);
		return jsonObject;
	}
	
	
	/**
	 * 退款回调
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/refund_result",method={RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public String refund_notify(HttpServletRequest request) {
		System.out.println("----------------退款回调-----------------------");
		String xmlMsg = HttpKit.readData(request);
		System.out.println("支付通知="+xmlMsg);
		Map<String, String> params = PaymentKit.xmlToMap(xmlMsg);
		String appid  = params.get("appid");
		//商户号
		String mch_id  = params.get("mch_id");
		String result_code  = params.get("result_code");
		String openId = params.get("openid");
		// 总金额
		String total_fee = params.get("total_fee");
        // 微信支付订单号
		String transaction_id = params.get("transaction_id");
		// 商户订单号
		String out_trade_no = params.get("out_trade_no");
		String out_refund_no = params.get("out_refund_no");


		return notify_url;
	}
	
	/**
	 * 支付回调
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/pay_result",method={RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public void pay_notify(HttpServletRequest request,HttpServletResponse response) throws IOException {
		// 支付结果通用通知文档: https://pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=9_7
        log.info("--------------支付回调！--------------------------");
		System.out.println("+++++++++++++++++++++++++++++++++++++++++++");
		InputStream inputStream ;
		StringBuffer sb = new StringBuffer();
		inputStream = request.getInputStream();
		String s ;
		BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
		while ((s = in.readLine()) != null){
			sb.append(s);
		}
		in.close();
		inputStream.close();
		Map<String, String> m = new HashMap<String, String>();
		try {
			m = WXUtil.doXMLParse(sb.toString());
		} catch (JDOMException e) {
			e.printStackTrace();
		}
		SortedMap<Object,Object> packageParams = new TreeMap<Object,Object>();
		Iterator it = m.keySet().iterator();
		while (it.hasNext()) {
			String parameter = (String) it.next();
			String parameterValue = m.get(parameter);
			String v = "";
			if(null != parameterValue) {
				v = parameterValue.trim();
			}
			packageParams.put(parameter, v);
			System.out.println("map文件之========================"+packageParams);
		}
			String resXml = "";
			if("SUCCESS".equals((String)packageParams.get("result_code"))){
				//得到返回的参数
				String openid = (String)packageParams.get("openid");
				String transaction_id = (String)packageParams.get("transaction_id");
				String out_trade_no = (String)packageParams.get("out_trade_no");
				String total_fee = (String)packageParams.get("total_fee");
				System.out.println(total_fee+"+++++++++++++++++++++++++++");
				Float fee = Float.parseFloat(total_fee)/100;
				//业务处理
				orderService.modifyOrderByNo(openid,out_trade_no,transaction_id,fee,new Date(),"1");

				resXml = "<xml>" + "<return_code><![CDATA[SUCCESS]]></return_code>"
						+ "<return_msg><![CDATA[OK]]></return_msg>" +"</xml> ";
				BufferedOutputStream out = new BufferedOutputStream(response.getOutputStream());
				out.write(resXml.getBytes());
				out.flush();
				out.close();
			} else {
				System.out.println("回调失败");
			}
		/*String xmlMsg = HttpKit.readData(request);
		System.out.println("支付通知="+xmlMsg);


	Map<String, String> params = PaymentKit.xmlToMap(xmlMsg);
	String appid  = params.get("appid");
	//商户号
	String mch_id  = params.get("mch_id");
	String result_code  = params.get("result_code");
	String openId   = params.get("openid");
	//交易类型
	String trade_type  = params.get("trade_type");
		//付款银行
	String bank_type   = params.get("bank_type");
	// 总金额
	String total_fee  = params.get("total_fee");
		System.out.println(total_fee+"===================");
	Float fee = (Float.parseFloat(total_fee))/100;
        //现金支付金额
	String cash_fee     = params.get("cash_fee");
	// 微信支付订单号
	String transaction_id      = params.get("transaction_id");
	// 商户订单号
	String out_trade_no      = params.get("out_trade_no");
	// 支付完成时间，格式为yyyyMMddHHmmss
	String time_end      = params.get("time_end");

		/////////////////////////////以下是附加参数///////////////////////////////////

		String attach      = params.get("attach");
//		String fee_type      = params.get("fee_type");
//		String is_subscribe      = params.get("is_subscribe");
//		String err_code      = params.get("err_code");
//		String err_code_des      = params.get("err_code_des");
		// 注意重复通知的情况，同一订单号可能收到多次通知，请注意一定先判断订单状态
		// 避免已经成功、关闭、退款的订单被再次更新
		orderService.modifyOrderByNo(out_trade_no,transaction_id,fee,new Date(),"1");
		if(PaymentKit.verifyNotify(params, WxPayApiConfigKit.getWxPayApiConfig().getPaternerKey())){
			if (("SUCCESS").equals(result_code)){
				//更新订单信息
				log.warn("更新订单信息:"+attach);
				//发送通知等
				Map<String, String> xml = new HashMap<String, String>();
				xml.put("return_code", "SUCCESS");
				xml.put("return_msg", "OK");
				return PaymentKit.toXml(xml);
			}
		}

		return xmlMsg;*/
	}

	@RequestMapping(value = "/reqOrderquery",method = {RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public Object reqOrderquery(@Param("openid") String openid,@Param("tradeState")String tradeState){
		List<Order> orders;
		if (tradeState==null){
			orders = orderService.queryOrderByOpenidAll(openid);
		}else {
			orders = orderService.queryOrderByOpenid(openid, tradeState);
		}
		JSONArray json = new JSONArray();
		for(Order order : orders){
			JSONObject jo = new JSONObject();
			jo.put("outTradeNo", order.getOutTradeNo());
			jo.put("transactionId",order.getTransactionId());
			jo.put("fee",order.getFee());
			jo.put("tradeState",order.getTradeState());
			jo.put("timeExpire",order.getTimeExpire());
			jo.put("timeStart",order.getTimeStart());
			jo.put("outRefundNo",order.getOutRefundNo());
			jo.put("body",order.getTbody());
			jo.put("refundSuccessTime",order.getRefundSuccessTime());
			json.add(jo);
		}
		return json;
	}



	@RequestMapping(value = "/wxAppQuery",method = {RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public Object wxAppQuery(@Param("userid") String userid,@Param("tradeState")String tradeState){
		List<Order> orders;
		if (tradeState==null){
			orders = orderService.queryOrderByUserIdAll(userid,"10");
		}else {
			orders = orderService.queryOrderByUserId(userid,tradeState,"10");
		}
		JSONArray json = new JSONArray();
		for(Order order : orders){
			JSONObject jo = new JSONObject();
			jo.put("outTradeNo", order.getOutTradeNo());
			jo.put("transactionId",order.getTransactionId());
			jo.put("fee",order.getFee());
			jo.put("tradeState",order.getTradeState());
			jo.put("timeExpire",order.getTimeExpire());
			jo.put("timeStart",order.getTimeStart());
			jo.put("outRefundNo",order.getOutRefundNo());
			jo.put("body",order.getTbody());
			jo.put("refundSuccessTime",order.getRefundSuccessTime());
			json.add(jo);
		}
		return json;
	}
}
