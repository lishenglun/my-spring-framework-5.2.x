package com.springstudy.service.impl;

import com.springstudy.dao.SystemDao;
import com.springstudy.service.SystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/23 12:48 上午
 */
@Component
public class SystemServiceImpl implements SystemService {

	//@Autowired
	//private SystemDao systemDao;

	@Override
	public void getUserInfo() {
		/**
		 * 调用systemDao的方法会打印：调用了invoke()方法...sql语句为：select * from SystemDao
		 */
		//systemDao.getUserInfo("张三");
		//systemDao.getUserInfoList();
		System.out.println("SystemServiceImpl getUserInfo ...");
	}
}