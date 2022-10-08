package com.springstudymvc.msb.other.converter.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/10/8 09:32
 */
@RestController
public class UserController {
	// http://localhost:8080/printData?data=2022-10-07
	@RequestMapping("/printData")
	public String data(@RequestParam("data") Date date) {
		System.out.println("data：" + date);
		return "success";
	}

}