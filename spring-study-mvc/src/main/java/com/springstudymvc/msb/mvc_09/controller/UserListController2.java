package com.springstudymvc.msb.mvc_09.controller;

import com.mashibing.controller.User;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 *
 * 测试内容：视图解析器，解析controller返回的逻辑视图名称，得到一个真正的物理视图地址，包装为一个View对象。
 *
 * @date 2022/7/28 5:03 下午
 */
@RequestMapping("/userlist123213213")
public class UserListController2 {

	@RequestMapping("/userlist123213213")
	public String userList(Model model){
		System.out.println("hahha");
		List<User> userList = new ArrayList<>();
		User user1 = new User("张三", 12);
		User user2 = new User("李四", 21);
		userList.add(user1);
		userList.add(user2);
		model.addAttribute("users",userList);
		return "userlist";
	}

}