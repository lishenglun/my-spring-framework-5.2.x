package com.springstudymvc.msb.mvc_10.controller.exception;

import com.springstudymvc.msb.mvc_10.LianException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * 测试异常解析器2：ResponseStatusExceptionResolver：处理带了@ResponseStatus()注解的异常类，例如{@link LianException}
 */
@Controller
public class TestSelfController {

	@RequestMapping("/lian")
	public ModelAndView lian(ModelAndView view) {
		view.setViewName("error");
		throw new RuntimeException();
	}

}
