package com.mashibing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class NoViewController {

    // 作业：思考一下，如何将当前对象直接返回？？
    @RequestMapping("/noView")
    @ResponseBody
    public User noView(ModelMap map){
        System.out.println("no view.......");
        User user = new User();
        user.setAge(12);
        user.setUsername("zhangsan");
//        map.put("user",user);
        return user;
    }

}
