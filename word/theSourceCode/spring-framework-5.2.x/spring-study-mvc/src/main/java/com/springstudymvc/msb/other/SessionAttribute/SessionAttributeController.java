package com.springstudymvc.msb.other.SessionAttribute;

import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 测试@SessionAttribute、@SessionAttributes：用于多次执行控制器方法间的参数共享
 * >>> value：用于指定存入的属性名称
 * >>> type：用于指定存入的数据类型。
 * @date 2022/7/24 10:46 上午
 */
@RequestMapping("/SessionAttributeController")
@SessionAttributes(value = {"username", "password"}, types = {Integer.class})
public class SessionAttributeController {

	/*

	1、@SessionAttributes中声明了要存储的参数名称是username，password，类型是Integer，
	2、所以在调用"/testPut"时，会将Model中的username，password、age对应的数据存入Session中，
	3、后面下一个请求，是请求"/testGet"时，就可以获取到对应的Model中的username，password、age中的数据！
	4、如果调用了"/testClean"会清理掉Session里面的数据；下次在调用"/testGet"时，就获取不到数据了

	所以@SessionAttributes起到了Session的效果，在多次请求间可以共享数据 —— 用于多次执行控制器方法间的参数共享

	 */

	/**
	 * 把数据存入SessionAttribute
	 *
	 * @param model
	 * @return String
	 * Model是spring提供的一个接口，该接口有一个实现类ExtendedModelMap
	 * 该类继承了ModelMap，而ModelMap就是LinkedHashMap子类
	 */
	@RequestMapping("/testPut")
	public String testPut(Model model) {
		model.addAttribute("username", "泰斯特");
		model.addAttribute("password", "123456");
		model.addAttribute("age", 31);
		// 跳转之前将数据保存到username、password和age中，因为注解@SessionAttributes中有这几个参数
		return "success";
	}

	/**
	 * 取出SessionAttribute
	 *
	 * @param model
	 * @return
	 */
	@RequestMapping("/testGet")
	public String testGet(ModelMap model) {
		System.out.println(model.get("username") + ";" + model.get("password") + ";" + model.get("age"));
		return "success";
	}

	/**
	 * 清除SessionAttribute
	 *
	 * @param sessionStatus
	 * @return
	 */
	@RequestMapping("/testClean")
	public String complete(SessionStatus sessionStatus) {
		sessionStatus.setComplete();
		return "success";
	}

}