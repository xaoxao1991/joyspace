package com.unicolour.joyspace.controller;

import com.unicolour.joyspace.dao.ManagerDao;
import com.unicolour.joyspace.dao.WeiXinPayConfigDao;
import com.unicolour.joyspace.dto.LoginManagerDetail;
import com.unicolour.joyspace.model.Manager;
import com.unicolour.joyspace.model.WeiXinPayConfig;
import com.unicolour.joyspace.service.ManagerService;
import com.unicolour.joyspace.util.Pager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class PaymentController {
	private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

	@Autowired
	WeiXinPayConfigDao weiXinPayConfigDao;

	@RequestMapping("/wx_pay/list")
	public ModelAndView adminUserList(
			ModelAndView modelAndView,
			@RequestParam(name="name", required=false, defaultValue="") String name,
			@RequestParam(name="pageno", required=false, defaultValue="1") int pageno) {

		Pageable pageable = new PageRequest(pageno - 1, 20);
		Page<WeiXinPayConfig> wxPayCfgs = name == null || name.equals("") ?
				weiXinPayConfigDao.findAll(pageable) :
				weiXinPayConfigDao.findByName(name, pageable);

		modelAndView.getModel().put("inputWxPayName", name);

		Pager pager = new Pager(wxPayCfgs.getTotalPages(), 7, pageno-1);
		modelAndView.getModel().put("pager", pager);

		modelAndView.getModel().put("wxpay_configs", wxPayCfgs.getContent());

		modelAndView.getModel().put("viewCat", "pay_mgr");
		modelAndView.getModel().put("viewContent", "wxpay_list");
		modelAndView.setViewName( "layout");

		return modelAndView;
	}

	@RequestMapping(path="/wx_pay/edit", method=RequestMethod.GET)
	public ModelAndView editWeiXinPay(
			ModelAndView modelAndView,
			@RequestParam(name="id", required=true) int id) {
		WeiXinPayConfig wxPay = null;
		if (id > 0) {
			wxPay = weiXinPayConfigDao.findOne(id);
		}

		if (wxPay == null) {
			wxPay = new WeiXinPayConfig();
		}

		modelAndView.getModel().put("wxPay", wxPay);
		modelAndView.setViewName("/pay/wxpay_edit :: content");

		return modelAndView;
	}

	@RequestMapping(path="/wx_pay/edit", method=RequestMethod.POST)
	@ResponseBody
	public boolean editWeiXinPay(
			@RequestParam(name="id", required=true) int id,
			@RequestParam(name="name", required=true) String name,
			@RequestParam(name="appId", required=true) String appId,
			@RequestParam(name="mchId", required=true) String mchId,
			@RequestParam(name="keyVal", required=true) String keyVal,
			@RequestParam(name="appSecret", required=true) String appSecret,
			@RequestParam(name="enabled", required=false, defaultValue = "false") boolean enabled) {
		WeiXinPayConfig wxPay = null;
		if (id > 0) {
			wxPay = weiXinPayConfigDao.findOne(id);
		}

		if (wxPay == null) {
			wxPay = new WeiXinPayConfig();
			wxPay.setName(name);
		}

		wxPay.setAppId(appId);
		wxPay.setMchId(mchId);
		wxPay.setKeyVal(keyVal);
		wxPay.setAppSecret(appSecret);
		wxPay.setEnabled(enabled);

		weiXinPayConfigDao.save(wxPay);
		return true;
	}

}