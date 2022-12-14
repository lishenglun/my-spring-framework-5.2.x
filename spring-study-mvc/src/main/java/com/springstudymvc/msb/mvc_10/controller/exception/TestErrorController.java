package com.springstudymvc.msb.mvc_10.controller.exception;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 测试异常解析器1、：ExceptionHandlerExceptionResolver：处理HandlerMethod抛出的异常
 */
@Controller
public class TestErrorController {
  
    @RequestMapping("/exception")
    public ModelAndView exception(ModelAndView view) throws ClassNotFoundException {
        view.setViewName("index");
        throw new ClassNotFoundException("class not found");
    }

    @RequestMapping("/nullpointer")
    public ModelAndView nullpointer(ModelAndView view) {
        view.setViewName("index");
        String str = null;
        str.length();
        return view;
    }

    // 当前定义的@ExceptionHandler只是归属于当前Controller
            
    @ExceptionHandler(RuntimeException.class)
    public ModelAndView error(RuntimeException error, HttpServletRequest request) {
        ModelAndView mav = new ModelAndView();
        mav.setViewName("error");
        mav.addObject("msg", "Runtime error");
        return mav;
    }

    @ExceptionHandler()
    public ModelAndView error(Exception error, HttpServletRequest request, HttpServletResponse response) {
        ModelAndView mav = new ModelAndView();
        mav.setViewName("error");
        mav.addObject("msg", "Exception error");
        return mav;
    }
    

    @ExceptionHandler(NullPointerException.class)
    public ModelAndView error(ModelAndView mav) {
        mav.setViewName("error");
        mav.addObject("msg", "NullPointer error");
        return mav;
    }
  
}