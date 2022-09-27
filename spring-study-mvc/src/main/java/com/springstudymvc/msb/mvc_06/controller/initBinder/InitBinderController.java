package com.springstudymvc.msb.mvc_06.controller.initBinder;

import com.springstudymvc.msb.mvc_06.domain.Student;
import com.springstudymvc.msb.mvc_06.domain.User;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 *
 */
@Controller
public class InitBinderController {

	/**
	 * 自定义传入日期格式
	 *
	 * @InitBinder：帮组我们完成最基本的前置功能
	 * @InitBinder作用于Controller中的方法，表示为当前控制器注册一个属性编辑器，同时可以设置属性的工作；
	 * 对webDataBinder进行初始化，且只对当前的Controller有效
	 *
	 * @param binder
	 */
	@InitBinder
	public void initBinder(WebDataBinder binder) {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		// 属性编辑器
		CustomDateEditor dateEditor = new CustomDateEditor(df, true);
		binder.registerCustomEditor(Date.class, dateEditor);
	}

	// 属性编辑器
	@RequestMapping("/param")
	public String getFormatDate(Date data, Map<String, Object> map) {
		System.out.println(data);
		map.put("name", "zhangsan");
		map.put("age", 12);
		map.put("date", data);
		return "map";
	}

	// 参数绑定
	@InitBinder("user")
	public void init1(WebDataBinder binder) {
		// 设置字段默认的前缀
		// 例如：表单里面保存的是2个对象的属性值，并且2个对象里面都有name这个属性，怎么办？一般是要通过一个前缀做区分，比如u.username，s.username
		binder.setFieldDefaultPrefix("u.");
	}

	@InitBinder("stu")
	public void init2(WebDataBinder binder) {
		// 设置字段默认的前缀
		binder.setFieldDefaultPrefix("s.");
	}

	@RequestMapping("/getBean")
	public ModelAndView getBean(User user, @ModelAttribute("stu") Student stu) {
		System.out.println(stu);
		System.out.println(user);
		String viewName = "success";
		ModelAndView modelAndView = new ModelAndView(viewName);
		modelAndView.addObject("user", user);
		modelAndView.addObject("student", stu);
		return modelAndView;
	}

}
