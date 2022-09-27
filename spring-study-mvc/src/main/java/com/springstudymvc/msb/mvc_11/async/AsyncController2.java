package com.springstudymvc.msb.mvc_11.async;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.concurrent.Callable;

@Controller
public class AsyncController2 {

    @ResponseBody
    @RequestMapping(value = "/callable",produces = "text/plain; charset=UTF-8")
    public Callable<String> callable(){
        System.out.println("Callable处理器主线程进入");
        Callable<String> callable = new Callable<String>() {
            @Override
            public String call() throws Exception {
                Thread.sleep(5 * 1000L);
                System.out.println("Callable处理执行中。。。");
                return "久等了";
            }
        };
        System.out.println("Callable处理器主线程退出");
        return callable;
    }

}