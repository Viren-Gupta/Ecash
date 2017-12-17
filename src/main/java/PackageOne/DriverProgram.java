package PackageOne;

import java.io.*;

import io.netty.util.internal.SystemPropertyUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class DriverProgram {

	static MainProgram obj;	
	
	public static void main(String[] args) throws IOException {
		Arrays.stream(new File("Database/").listFiles()).forEach(File::delete);

		System.out.println("Go to http://localhost:8080/ecash");
		System.out.println("Kindly ensure that port 8080 and 8081 are free");
		
		
		Vertx vertx=Vertx.vertx();
		
		Router router = Router.router(vertx);
		
		router.route("/ecash").handler(res->{
			
		if(res.request().getParam("start")!=null || res.request().getParam("stop")!=null){
				int numPos=Integer.parseInt(res.request().getParam("pos"));
				int numKiosk=Integer.parseInt(res.request().getParam("kiosk"));
				int numTransactions=Integer.parseInt(res.request().getParam("transRate"));
				int numUsers=Integer.parseInt(res.request().getParam("users"));
				if(res.request().getParam("start")==null){
					//stop
					
				}else{
					//start
					obj=new MainProgram(numKiosk,numPos,numUsers,numTransactions);
					vertx.deployVerticle(obj);
				}
				
		}
		
		if(res.request().getParam("refresh_items")!=null){ //top items
			Future<Void> f=Future.future();
			if(obj==null)
				System.out.println("Donot");
			else{
					obj.perform(ans->{
						if(ans.succeeded()){
						}
					}, f);
				}
			
		}
			
			HttpServerResponse response = res.response();
			response
		       .putHeader("content-type", "text/html")
		       .end("<html>"
		       		+ "<script>"
		       		+ "function getTopItems(){"
		       		+ "var xmlHttp = new XMLHttpRequest();"
		       		+ "xmlHttp.open( 'GET', 'http://localhost:8081/getAnswer',true);"
		       		+ "xmlHttp.setRequestHeader('content-type','text/plain');"
		       		+ "xmlHttp.onreadystatechange = function(){"
		       		+ "if(xmlHttp.readyState == 4 && xmlHttp.status==200){"
		       		+ "var i=document.getElementById('topItems');"
		       		+ "var x='ItemName=Quantity';"
		       		+"var j=document.getElementById('r1');"
		       		+"j.innerHTML=xmlHttp.responseText;"
		       			+ "i.innerHTML=x;"
		       		+ "alert(xmlHttp.responseText);"
		       		+ "}"
		       		+ "};"
		       		+ "xmlHttp.send();"
		       		+ "return false;"
		       		+ "}"
		       		
					+ "function getTopPos(){"
					+ "var xmlHttp = new XMLHttpRequest();"
					+ "xmlHttp.open( 'GET', 'http://localhost:8081/getAnswer1',true);"
					+ "xmlHttp.setRequestHeader('content-type','text/plain');"
					+ "xmlHttp.onreadystatechange = function(){"
					+ "if(xmlHttp.readyState == 4 && xmlHttp.status==200){"
					+ "var i=document.getElementById('topPos');"
					+ "var x='PosName AmountOfSales';"
					+"var j=document.getElementById('r2');"
					+"j.innerHTML=xmlHttp.responseText;"
						+ "i.innerHTML=x;"
					+ "alert(xmlHttp.responseText);"
					+ "}"
					+ "};"
					+ "xmlHttp.send();"
					+ "return false;"
					+ "}"
	
		       		
		       		+ "</script>"
		       		+ "<body>"
		       		+ "<div style=height:10%;background-color:#E3F2FD>"
		       		+ "<h1 align=center>POS Transaction Reporting and Management Portal</h1>"
		       		+ "</div>"
		       		+ "<div style=height:30%;background-color:#E3F2FD align=center>"
		       		+ "<form method=get style=padding:10px>"
		       		+ "<input type=number required=true placeholder=Number_of_POS name=pos><br<br>"
		       		+ "<input type=number required=true placeholder=Number_of_KIOSK name=kiosk><br><br>"
		       		+ "<input type=number required=true placeholder=Number_of_Users name=users><br<br>"
		       		+ "<input type=number required=true placeholder=Number_of_transacation name=transRate>"
		       		+ "<input type=submit name=start value=start><br><br>"
		       		+ "</form>"
		       		+ "</div>"
		       		+ "<div style=height:30%;background-color:#E3F2FD>"
		       		+ "<h1>Top Selling Items today</h1>"
		       		+ "<h2 id='topItems'></h2>"
		       		+ "<h3 id='r1'></h3>"
		       		+ "<form method=get>"
		       		+ "<button type=submit name=refresh_items value=Refresh onclick='return getTopItems()'>Refresh</button>"
		       		+ "</form>"
		       		+ "</div>"
		       		+ "<div style=height:30%;background-color:#E3F2FD>"
		       		+"<h1>POS sales today</h1>"
		       		+ "<h2 id='topPos'></h2>"
		       		+ "<h3 id='r2'></h3>"
		       		+"<form method=get>"
		       		+ "<button type=submit name=refresh_pos value=Refresh onclick='return getTopPos()'>Refresh</button>"
		       		+ "</form>"
		       		+ "</div>"
		       		+ "</body>"
		       		+ "</html>");
				
		});
		
		HttpServer server = vertx.createHttpServer();
		server.requestHandler(router::accept).listen(8080);
		
		
	}
	
	public static int getFreePort() throws IOException{
		ServerSocket socket=new ServerSocket(0);
		int port=socket.getLocalPort();
		socket.close();
		return port;
	}

}
