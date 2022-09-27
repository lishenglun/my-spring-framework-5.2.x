package com.springstudymvc.msb.mvc_10.controller.exception;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;

/**
 * 测试异常解析器3：DefaultHandlerExceptionResolver：处理特定异常类型。当然这些异常可能也是由HandlerMethod抛出的，但是其它的异常处理器没有处理，于是到这里了，这里可以处理对应的一些特定异常
 */
@Controller
public class TestDefaultController {

    @RequestMapping("/default")
    public ModelAndView noHandlerMethod(ModelAndView view, HttpServletRequest request) throws NoHandlerFoundException {
        view.setViewName("error");
        throw new NoHandlerFoundException(null,null,null);
    }

}
