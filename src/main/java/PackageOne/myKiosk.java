package PackageOne;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.core.AsyncResult;
import io.vertx.core.shareddata.LocalMap;


public class myKiosk{
	private int portNum;
	private String name;
	private SQLConnection conn;
	private Vertx vertx;
	private String dbPath;
	
	public myKiosk(int portNum, String name,Vertx vertx,String dbPath) {
		this.portNum = portNum;
		this.name = name;
		this.vertx=vertx;
		this.dbPath=dbPath;
	}

	public void establishConnection(Handler<AsyncResult<SQLConnection>> next, Future<Void> fut) throws IOException {
		String url=dbPath+"file_"+Integer.toString(this.portNum)+".db";
		
		//System.out.println(url);
		JsonObject config=new JsonObject();
		config.put("url",url);
		config.put("driver_class","org.sqlite.JDBC");
		JDBCClient jdbc = JDBCClient.createNonShared(vertx,config);
		
		jdbc.getConnection(ar -> {
	      if (ar.failed()) {
	        fut.fail(ar.cause());
	      } else {
	        next.handle(Future.succeededFuture(ar.result()));
	      }
	    });
	  }
	
	public void initializeDB(AsyncResult<SQLConnection> result,Handler<AsyncResult<Void>> next, Future<Void> fut){
		if(result.failed())
			fut.fail(result.cause());
		else{
			this.conn=result.result();
			conn.execute("CREATE TABLE IF NOT EXISTS Balance_reg(ui INTEGER AUTO_INCREMENT,id INTEGER,balance "
					+ "double,primary key(ui))",ar1->{
						if(ar1.succeeded()){
							System.out.println("db created successfully");
							next.handle(Future.<Void>succeededFuture());
							return;
						}
						else{
							System.out.println("db creation failed");
							fut.fail(ar1.cause());
							return;
						}
					});
		}
	}
	
	public void createServer(Handler<AsyncResult<SQLConnection>> next, Future<Void> fut){
		HttpServer server=vertx.createHttpServer();
		server.requestHandler(request->{
			String p=request.path();
			
			if(p.equals("/issueCard")){
				double amount=Double.parseDouble(request.getHeader("amount"));
				int cardID=Integer.parseInt(request.getHeader("cardID"));
				
				
				Future<Void> fut1=Future.future();
				
				updateDB(ans->sendToOthers(res->{
					if(res.succeeded())
						;
						//System.out.println("sent to others");
					else
						System.out.println("propagation failed");
				},cardID,amount,fut1),amount,cardID,fut1);
				
			}
		});


		server.listen(this.portNum,"localhost",res->{
			if(res.succeeded()){
				next.handle(Future.succeededFuture());
				//System.out.println("Started kiosk on port "+this.portNum);
			}
			else
				System.out.println("Failed starting kiosk on port ");
		});
	}
	
	private void sendToOthers(Handler<AsyncResult<Void>> next, int cardID,double amount, Future<Void> fut1) {
		
		SharedData sd=vertx.sharedData();
		LocalMap<String,Buffer> map=sd.getLocalMap("portMap");
		Buffer l=map.get("listOfPorts_pos");
		String t=(l.getString(0,l.length()));
		
		Buffer server=map.get("listOfPorts_server");
		t=(server.getString(0,server.length()))+" "+t;
		
		String[] listOfPorts_temp=t.split(" ");

		int[] listOfPorts_pos=new int[listOfPorts_temp.length];
		for(int i=0;i<listOfPorts_temp.length;i++)
			listOfPorts_pos[i]=Integer.parseInt(listOfPorts_temp[i]);

		HttpClient client=null;
		if(listOfPorts_pos.length>1)
			client=vertx.createHttpClient();
		for(int i=0;i<listOfPorts_pos.length;i++){
			if(listOfPorts_pos[i]==this.portNum)
				continue;
			
			HttpClientRequest req=client.post(listOfPorts_pos[i],"localhost","/update",response->{
				//System.out.println("sending ...");
			});
			
			req.putHeader("sender", Integer.toString(this.portNum));
			req.putHeader("cardNum",Integer.toString(cardID));
			req.putHeader("balance",Double.toString(amount));
			
			req.end();
			//System.out.println("leave sending");
			
		}
		
		next.handle(Future.succeededFuture());
		
		
	}

	public void updateDB(Handler<AsyncResult<Void>> next,double amount,int cardID ,Future<Void> fut){
		//System.out.println("in updateDB");
		
		String d=cardID+","+amount;
		String q="INSERT INTO Balance_reg (id,balance) VALUES ("+d+")";
		
		this.conn.execute(q,ins->{
			if(ins.succeeded()){
				//System.out.println("update_bal succesfully");
				next.handle(Future.succeededFuture(ins.result()));
				return;
			}
			else{
				System.out.println("updated_bal fail");
				
				fut.fail(ins.cause());
				return;
			}
		});
	}

	public void startProcess(Handler<AsyncResult<Void>> next, Future<Void> fut1) throws Exception {
		
		Future<Void> fut=Future.future();
		establishConnection((connection)-> initializeDB(connection,init->{
			createServer(last->{
				if(last.succeeded()){
					//System.out.println("finished creating server");
					next.handle(Future.succeededFuture());
				}
				else{
					fut1.fail(last.cause());
					System.out.println(last.cause());
				}
			},fut);
		},fut),fut);
		
	}
}
