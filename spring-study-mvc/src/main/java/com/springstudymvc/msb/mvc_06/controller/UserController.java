package com.springstudymvc.msb.mvc_06.controller;

import com.springstudymvc.msb.mvc_06.domain.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/7/28 1:38 下午
 */
@Controller
public class UserController {

	@RequestMapping("/getUser")
	@ResponseBody
	public User getUser() {
		User user = new User();
		user.setAge(1);
		user.setName("zhangsan");
		return user;
	}

}