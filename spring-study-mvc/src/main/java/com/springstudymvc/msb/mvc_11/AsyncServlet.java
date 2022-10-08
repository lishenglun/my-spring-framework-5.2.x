package com.springstudymvc.msb.mvc_11;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 自定义原始的异步servlet
 * @date 2022/8/2 8:28 下午
 */
public class AsyncServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("text/plain;charset=utf-8");

		PrintWriter writer = resp.getWriter();
		writer.println("检查工作");
		writer.flush();

		// 假设有一堆的job需要完成，每一个job代表具体的业务处理逻辑
		List<String> list = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			list.add("job" + i);
		}

		// 可以使用request中的startAsync来进行启动
		// AsyncContext等同于Servlet中的ServletContext，保存异步请求执行过程中的相关信息
		AsyncContext asyncContext = req.startAsync();
		/**
		 * 1、注意：不会打印监听器中onStartAsync()的内容，因为当前监听器是在"req.startAsync()"之后添加的！
		 */
		asyncContext.addListener(new MyListener());

		// 模拟执行核心处理逻辑
		doWork(asyncContext, list);

		writer.println("任务布置完成");
		writer.flush();
	}

	private void doWork(AsyncContext ac, List<String> list) {
		// 核心业务处理逻辑

		// 设置超时时间
		ac.setTimeout(60 * 60 * 1000L);
		// 开启新线程来执行具体的处理逻辑
		ac.start(new Runnable() {
			@Override
			public void run() {
				try {
					PrintWriter writer = ac.getResponse().getWriter();
					for (String job : list) {
						writer.println(job + "正在执行过程中..");
						Thread.sleep(1000L);
						writer.flush();
					}
					// 当请求执行完毕，需要给定完成通知
					ac.complete();
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
			}
		});
	}

}