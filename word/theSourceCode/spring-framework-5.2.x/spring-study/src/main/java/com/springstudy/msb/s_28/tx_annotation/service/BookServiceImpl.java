package com.springstudy.msb.s_28.tx_annotation.service;

import com.springstudy.msb.s_28.tx_annotation.dao.BookDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/6/16 5:04 下午
 */
@Component
public class BookServiceImpl implements BookService {

	@Autowired
	private BookDao bookDao;

	@Transactional
	@Override
	public void updateBalance(String userName, int money) {
		//try {
		bookDao.updateBalance(userName, money);
		//}catch (Exception e){
		//	e.printStackTrace();
		//}
	}

}