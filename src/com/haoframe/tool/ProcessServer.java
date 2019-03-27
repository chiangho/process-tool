package com.haoframe.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ProcessServer {
	
	
	public static void main(String[] args) throws InterruptedException {
		ProcessServer processServer = new ProcessServer();
		//服务信息  window系统则为端口。Linux则为启动文件的名称
		String serviceInfo       = args[0];
		String runCommand        = args[1];
		
//		String serviceInfo       = "8088";
//		//String runCommand        = "java -jar E:\\13Service\\ml-permission-0.0.1-SNAPSHOT.jar";
//		String runCommand        = "E:\\13Service\\start.vbs";
		
		String isStartProcess    = "Y";
		if(args.length>=3) {
			isStartProcess = args[2].toUpperCase();
			if(!isStartProcess.equals("Y")&&!isStartProcess.equals("N")) {
				isStartProcess    = "Y";
			}
		}
		//获取当前线程
		String name = ManagementFactory.getRuntimeMXBean().getName();  
		String currentPid = name.split("@")[0];  
		
		//校验参入是否有效
		if(serviceInfo==null||serviceInfo.length()==0) {
			System.out.println("缺少入参：服务信息，window系统传入服务的端口，Linux系统传入启动文件的名称");
			return;
		}
		switch(processServer.os) {
			case window:
				int portNumber = 0;
				try {
					portNumber = Integer.parseInt(serviceInfo);
				}catch(Exception e) {
					System.out.println("入参错误：端口值要求是数字而你提供的是==>"+serviceInfo);
					e.printStackTrace();
					return;
				}
				if(portNumber <= 0 ) {
					System.out.println("入参错误：端口值要求大于零");
					return;
				}
				processServer.setTagPort(portNumber);
				break;
			case linux:
				processServer.setServiceName(serviceInfo);
				break;
		}
		if(runCommand==null||runCommand.length()==0) {
			System.out.println("缺少入参：启动命令");
			return;
		}
		//查询进程
		Set<Integer> pids = processServer.findPid();
		if(pids!=null&&pids.size()>0) {
			//关闭进程
			processServer.killWithPid(pids,Integer.parseInt(currentPid));
		}
		//启动新进程
		if(isStartProcess.equals("Y")) {
			processServer.runCommand(runCommand);
		}
	}
	
	
	/**
	 * 构造函数
	 */
	private ProcessServer() {
		String osTitle = System.getProperties().getProperty("os.name");
		if(osTitle.toLowerCase().contains("windows")) {
			this.setOs(OS.window);
		}else {
			this.setOs(OS.linux);
		}
		System.out.println("===========操作系统是:"+this.os.getName()+"===========");
	}
	/**
	 * 操作系统类型
	 */
	private OS os;
	public OS getOs() {
		return os;
	}
	public void setOs(OS os) {
		this.os = os;
	}

	/**
	 * 服务的端口号
	 */
	private int tagPort;
	public int getTagPort() {
		return tagPort;
	}
	public void setTagPort(int tagPort) {
		this.tagPort = tagPort;
	}

	/**
	 * 服务的文件名称
	 */
    private String serviceName;
	public String getServiceName() {
		return serviceName;
	}
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	/***
	 * 查找有效的进程号
	 * @return
	 */
	public Set<Integer> findPid(){
		Runtime runtime = Runtime.getRuntime();
		String findCommand = "";
		switch(this.os) {
		case window:
			findCommand = this.os.getFindPidCommand(this.tagPort+"");
			break;
		case linux:
			findCommand = this.os.getFindPidCommand(this.serviceName);
			break;
		}
		System.out.println("查询进程的命令为"+findCommand);
		if(findCommand==null||findCommand.length()==0) {
			return null;
		}
		List<String> data = new ArrayList<>();
		try {
			Process p = runtime.exec(findCommand);
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = null;
			while((line = reader.readLine())!=null){
				boolean validPort = this.os.validPort(line, tagPort,this.serviceName);
				if (validPort) {
					System.out.println("============截取有效进程行=========="+line);
					data.add(line);
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		if (data!=null&&data.size() > 0) {
			return this.os.parsePortToPid(data);
		} 
		return null;
	}
	
	/**
	 * 杀死进程
	 * @param pids           目标进程
	 * @param currentPid     当前进程
	 */
	public void killWithPid(Set<Integer> pids,int currentPid) {
		for (Integer pid : pids) {
			try {
				if(!pid.equals(currentPid)) {
					System.out.println("杀死进程"+pid);
					Process process = Runtime.getRuntime().exec(this.os.getKillProcessCommand(pid+""));
					InputStream inputStream = process.getInputStream();
					String txt = readTxt(inputStream);
					System.out.println(txt);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	/**
	 * 读取执行结果
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private String readTxt(InputStream in) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		StringBuffer sb = new StringBuffer();
		String line;
		while ((line = reader.readLine()) != null) {
			sb.append(line);
		}
		reader.close();
		return sb.toString();
	}
	
	/**
	 * 执行命令
	 * @param command
	 */
	private void runCommand(String command) {
		try {
			Runtime.getRuntime().exec(command);
			System.out.println("执行成功");
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

	
	
	//////////////////////////////////////定义系统类型的枚举///////////////////////////////////////////////////////////
	enum OS{
		
		window(1,"Windows操作系统","cmd /c netstat -ano | findstr \"#SERVICEINFO#\"","taskkill /F /pid #PID#"),
		linux(2,"Linux操作系统","ps -ef","kill -9 #PID# ");
		
		/**
		 * 编号
		 */
		private int code;
		/**
		 * 名称
		 */
		private String name;
		/**
		 * 查询进程命令
		 */
		private String findPidCommand;
		/**
		 * 杀死进程命令
		 */
		private String killProcessCommand;
		
		private OS(int code,String name,String findPidCommand,String killProcessCommand) {
			this.code = code;
			this.name = name;
			this.findPidCommand = findPidCommand;
			this.killProcessCommand = killProcessCommand;
		}
		public int getCode() {
			return code;
		}
		public void setCode(int code) {
			this.code = code;
		}
		public String getName() {
			return name;
		}
		public String getFindPidCommand(String serviceInfo) {
			return findPidCommand.replace("#SERVICEINFO#", serviceInfo);
		}
		public String getKillProcessCommand(String pid) {
			return killProcessCommand.replace("#PID#", pid);
		}
		
		/**
		 * 校验是否有效的结果行
		 * @param line           命令结果行
		 * @param tagPort        目标端口
		 * @param servicePath    服务jar包名称
		 * @return
		 */
		public boolean validPort(String line,int tagPort,String serviceName) {
			if(this.code==1) {//window
				Pattern pattern = Pattern.compile("^ *[a-zA-Z]+ +\\S+");
				Matcher matcher = pattern.matcher(line);
				matcher.find();
				String find = matcher.group();
				int spstart = find.lastIndexOf(":");
				find = find.substring(spstart + 1);
				int port = 0;
				try {
					port = Integer.parseInt(find);
				} catch (NumberFormatException e) {
					return false;
				}
				if (tagPort==port) {
					return true;
				} else {
					return false;
				}
			}else {
				if(line.contains("java -jar")&&line.contains(serviceName)) {
					return true;
				}else {
					return false;
				}
			}
		}
		
		/**
		 * 从结果行中解析出进程编号
		 * @param data  有效结果行集合
		 * @return
		 */
		public Set<Integer> parsePortToPid(List<String> data) {
			Set<Integer> pids = new HashSet<>();
			for (String line : data) {
				if(this.code==1) {//window
					int offset = line.lastIndexOf(" ");
					String spid = line.substring(offset);
					spid = spid.replaceAll(" ", "");
					int pid = 0;
					try {
						pid = Integer.parseInt(spid);
						if(pid>0) {
							pids.add(pid);
						}
					} catch (NumberFormatException e) {
						System.out.println("获取的进程号错误:" + spid);
					}
				}else {//linux
					String[] strs = line.split("\\s+");
					int pid = 0;
					try {
						pid = Integer.parseInt(strs[1]);
						if(pid>0) {
							pids.add(pid);
						}
					} catch (NumberFormatException e) {
						System.out.println("获取的进程号错误:" + pid);
					}
				}
			}
			return pids;
		}
	}
}
