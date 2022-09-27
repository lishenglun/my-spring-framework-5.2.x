package com.springstudymvc.msb.mvc_09.controller;

import com.mashibing.controller.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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
@Controller
public class UserListController {

	@RequestMapping("/userlist")
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

	/**
	 * 问题：Controller中用@ResponseBody返回对象时，报错500：No converter found for return value of type: class com.mashibing.controller.User
	 *
	 * 解决方案：
	 *
	 * （1）添加jackson的jar包
	 * jackson-databind.jar
	 * jackson-core.jar
	 * jackson-annotations.jar
	 *
	 * 题外：添加好jar包运行Tomcat，可能会报错com/fasterxml/jackson/databind/exc/InvalidDefinitionException。
	 * 这是由于jackson的jar包版本冲突引起的。所以上面的三个jar的版本越高越好，我的是2.9.6版本的jar包。
	 *
	 * （2）在springmvc的配置文件中，添加以下
	 * <mvc:annotation-driven>
	 *      <mvc:message-converters>
	 *             <bean class="org.springframework.http.converter.StringHttpMessageConverter"/>
	 *             <bean class="org.springframework.http.converter.json.MappingJackson2HttpMessageConverter"/>
	 *    </mvc:message-converters>
	 * </mvc:annotation-driven>
	 *
	 * （3）此外还需要在配置文件中添加MVC的前缀：（红色部分）
	 * <?xml version="1.0" encoding="UTF-8"?>
	 * <beans xmlns="http://www.springframework.org/schema/beans"
	 * 		xmlns:mvc="http://www.springframework.org/schema/mvc"
	 * 		xsi:schemaLocation="http://www.springframework.org/schema/mvc
	 * 		http://www.springframework.org/schema/mvc/spring-mvc-3.0.xsd">
	 *
	 * （4）完成以上步骤后，就可以使用@ResponseBody返回对象啦
	 *
	 * 参考：https://blog.csdn.net/east123321/article/details/81258927 —— 《spring mvc使用@ResponseBody报错No converter found for return value of **》
	 */
	@RequestMapping("/getUser")
	@ResponseBody
	public User getUser(Model model){
		User user1 = new User("张三", 12);
		return user1;
	}

}