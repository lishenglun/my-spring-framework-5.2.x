package com.springstudy.msb.s_27.tx_xml.service;


import com.springstudy.msb.s_27.tx_xml.dao.BookDao;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/6/14 4:38 下午
 */
public class BookServiceImpl implements BookService {

	private BookDao bookDao;

	private UserService userService;

	@Override
	public void updateBalanceInService(String userName, int money) {
		System.out.println("。。。。。。。。。。方法执行前");
		//try {
		//bookDao.updateBalanceInDao(userName, money);
		//}catch (Exception e){
		//	e.printStackTrace();
		//}
		System.out.println("。。。。。。。。。。方法执行后");

		userService.updateName(1);

	}

	public BookDao getBookDao() {
		return bookDao;
	}

	public void setBookDao(BookDao bookDao) {
		this.bookDao = bookDao;
	}

	public UserService getUserService() {
		return userService;
	}

	public void setUserService(UserService userService) {
		this.userService = userService;
	}
}