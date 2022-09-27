package com.springstudymvc.msb.mvc_11.async;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.WebAsyncTask;

import java.util.concurrent.Callable;

@Controller
public class AsyncController {

	/**
	 * 异步执行的原理：
	 * 主线程执行到某个步骤之后，开启了一个线程分支，当开启了分支之后，主线程继续接着往下走，该怎么走就怎么走，
	 * 分支同时也在往下走，当分支执行完结果之后，再把结果返回给我们当前的一个客户端，就完事了
	 *
	 * 注意：主线程不会先返回一些数据！如果想返回前端数据，需要一个ModelAndView，因为ModelAndView里面会承载我们对应的一些数据，
	 * 但是如果是一个异步请求，主线程返回的直接是一个null，而不是ModelAndView对象，主线程返回的是null，就不会走视图渲染和回显示的逻辑了，
	 * 然后等主线程走完所有逻辑，主线程就被销毁了（所以说，主线程返回null，相当于中断主线程了），只能等着异步请求的线程进行返回ModelAndView；
	 *
	 * 注意：不是阻塞，主线程没有阻塞，它会接着往下走，主线程已经执行完了，后面会有一个异步线程在等着返回结果，没有阻塞的过程，这里不涉及到任何阻塞的东西。
	 * 异步线程走自己的，主线程还是接着往下走，主线程不会等待异步线程执行完毕，只是主线程执行完毕了之后，可能异步线程还没有执行完毕，所以前端没有得到响应结果而已！
	 * 所以在前端看起来是阻塞了的！
	 *
	 * @return
	 */
	/**
	 * 1、主线程必须返回异步的WebAsyncTask，然后WebAsyncTask才启动吗？
	 * 对
	 *
	 * 2、可不可以自己设计一个先返回主线程数据，然后异步处理完成之后再返回异步处理的数据？
	 * 不可以，这是违反spring mvc设计原则的。想返回前端数据，需要一个ModelAndView对象，这个过程在执行过程中，是由异步请求直接返回的，
	 * 不可能说主线程返回一个ModelAndView对象，然后异步线程再返回一个ModelAndView对象，这不可能，这是做不到的！
	 *
	 * 题外：最关键点，在返回值处理器，里面判断类型来进行处理
	 *
	 * 3、主线程如果没有被阻塞，那么最终返回的mv是哪个在处理？
	 * 这是个错误的问题，主线程不会被阻塞，只是当异步线程没有处理完成返回结果的时候，主线程返回的是null，而不是mv，然后主线程就不会响应数据到前端，接着就等执行完毕所有剩下的逻辑，就销毁了！
	 * 异步处理在进行处理的时候，不是建立了多次连接，建立的连接只有一次，连接通道一直是开着的，刚开始发送一个请求过来，是一个线程来进行处理的，称之为主线程，
	 * 主线程处理完成之后，直接把这个主线程关掉了，关掉的是线程，而不是连接，后台会有一个异步的线程来进行执行，当异步线程执行完毕之后，会沿着连接，把结果返回给客户端，
	 * 所以在页面里面可以看到执行结果
	 *
	 * @return
	 */
    @ResponseBody
    @RequestMapping(value = "/webasynctask",produces = "text/plain; charset=UTF-8")
    public WebAsyncTask<String> webAsyncTask() {
        System.out.println("WebAsyncTask处理器主线程进入");
        // 创建任务对象
        WebAsyncTask<String> task = new WebAsyncTask<String>(new Callable<String>() {
        	// 有返回值，才能响应，这样我才能知道到底是执行成功了，还是执行失败了
			// 要不然，开启一个异步任务，异步任务执行完了，我也不知道到底有没有执行完，没有任何反馈，无法判断
			// 所以一般情况下，都会有返回值
            @Override
            public String call() throws Exception {
                Thread.sleep(20*1000L);
                System.out.println("WebAsyncTask处理执行中。。。");
                return "久等了";
            }
        });
        System.out.println("WebAsyncTask处理器主线程退出");
        return task;
    }

}