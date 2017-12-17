package PackageOne;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Date;
import java.text.SimpleDateFormat;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.core.AsyncResult;
import io.vertx.core.shareddata.LocalMap;


public class myPosk{
	private int portNum;
	private String name;
	private SQLConnection conn;
	private Vertx vertx;
	private String dbPath;
	
		public myPosk(int portNum, String name,Vertx vertx,String dbPath) {
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
			conn.execute("CREATE TABLE IF NOT EXISTS CBR(ui INTEGER AUTO_INCREMENT,id INTEGER,transNum INTEGER"
					+ ",location VARCHAR(100),amount double,timeOfTransaction VARCHAR(100),"
					+ "itemName VARCHAR(200),quantity INTEGER,primary key(ui))",
					ar->{
						if(ar.failed()){
							System.out.println("error");
							System.out.println(ar.cause());
							fut.fail(ar.cause());
							return;
						}else{

							conn.execute("CREATE TABLE IF NOT EXISTS Balance_reg(ui INTEGER AUTO_INCREMENT,id INTEGER,balance "
									+ "double,primary key(ui))",ar1->{
										if(ar1.succeeded()){
											System.out.println("db created successfully");
											next.handle(Future.<Void>succeededFuture());
											return;
										}
										else{
											System.out.println("db creation failed");
											fut.fail(ar.cause());
											return;
										}
									});

						}
					}
					);
		}
	}


	MyCard temp;
	public void createServer(Handler<AsyncResult<SQLConnection>> next, Future<Void> fut){
		HttpServer server=vertx.createHttpServer();
		server.requestHandler(request->{
			String p=request.path();


			if(p.equals("/update")){
				int senderPort=Integer.parseInt(request.getHeader("sender"));
//				System.out.println(this.portNum+" received update POS request from "+senderPort);

				Future<Void> fut_a=Future.future();


				updateDatabase_fromKiosk(result->{
					if(result.succeeded()){
						
						//System.out.println("update pos successfully on request sent from "+senderPort);
					}
					else
						System.out.println("update failed on request sent from "+senderPort);
				},
				Integer.parseInt(request.getHeader("cardNum"))
				, Double.parseDouble(request.getHeader("balance")),
				fut_a);
			}

			if(p.equals("/update_pos")){
				int senderPort=Integer.parseInt(request.getHeader("sender"));
				//System.out.println(this.portNum+" received update request from POS "+senderPort);

						
				long timeID=vertx.setPeriodic(2000,new Handler<Long>(){

					@Override
					public void handle(Long arg0) {
						//System.out.println("fired->>>>>>> "+arg0);

						conn.query("select * from Balance_reg where id="+Integer.parseInt(request.getHeader("cardNum")),select->{
							if(select.succeeded()){
								int rows=select.result().getNumRows();
								if(rows==1){
									Future<Void> fut_a=Future.future();

									updateMyRegister(result->{
										if(result.succeeded()){
											//System.out.println("update successfully on request sent from "+senderPort);
											vertx.cancelTimer(arg0);

										}
										else
											System.out.println("update failed on request sent from "+senderPort);
									},
									Integer.parseInt(request.getHeader("cardNum"))
									, request.getHeader("location"),Double.parseDouble(request.getHeader("amount")),
									request.getHeader("date"),request.getHeader("itemName"),Integer.parseInt(request.getHeader("quantity")),fut_a);

									
								}
							}else{
								System.out.println("failed select");
							}
						});

					}

				});
				
			
			}


			if(p.equals("/transaction")){

				System.out.println("Started transacting--------------------->");

				double amount=Double.parseDouble(request.getHeader("amount"));
				int cardID=Integer.parseInt(request.getHeader("cardID"));
				String itemName=request.getHeader("itemName");
				int quantity=Integer.parseInt(request.getHeader("quantity"));

				Date d=new Date();
				SimpleDateFormat sdfr = new SimpleDateFormat("dd/MMM/yyyy");
				String dateString = sdfr.format(d);

				long timeID=vertx.setPeriodic(2000,new Handler<Long>(){

					@Override
					public void handle(Long arg0) {
						//System.out.println("fired->>>>>>> "+arg0);

						conn.query("select * from Balance_reg where id="+cardID,select->{
							if(select.succeeded()){
								int rows=select.result().getNumRows();
								if(rows==1){
									Future<Void> fut1=Future.future();

									updateMyRegister(ans->sendToOthers(res->{

											if(res.succeeded()){
												//System.out.println("sent to others");
												vertx.cancelTimer(arg0);
											}
											else
												System.out.println("propagation failed");
										},temp,fut1),cardID
										,name,amount,
										dateString,itemName,quantity,fut1);

								}
							}else{
								System.out.println("failed select");
							}
						});

					}

				});

			}
		});


		server.listen(this.portNum,"localhost",res->{
			if(res.succeeded()){
				next.handle(Future.succeededFuture());
				//System.out.println("Started pos on port "+this.portNum);
			}
			else
				System.out.println("Failed starting pos on port ");
		});
	}

	private void updateDatabase_fromKiosk(Handler<AsyncResult<Void>> next, int cardID,
			double balance, Future<Void> fut_a) {
		
		String q1="insert into Balance_reg(id,balance) values("+cardID+","+balance+")";

		this.conn.execute(q1,res2->{
			if(res2.succeeded()){
				//System.out.println("inserted cardID into pos db succesfully");
				next.handle(Future.succeededFuture());
				return;
			}else{
				System.out.println("inserted cardID into pos db failed");
				fut_a.fail(res2.cause());
				return;
			}
		});

	}


	private void updateMyRegister(Handler<AsyncResult<Void>> next, int cardNum,
			String location, double amount,
			String date,String itemName ,int quantity,Future<Void> fut_a) {

		String query="SELECT * from CBR WHERE id="+cardNum+" order by transNum asc";
		this.conn.query(query,sel->{
			if(sel.succeeded()){
				//System.out.println("select query successfull");
				
				int numRows=sel.result().getNumRows();
				Future<Void> fut_my=Future.future();

				MyCard newest_temp=new MyCard(cardNum,amount,new MyTransaction(location,amount,date,itemName,quantity));
				
				if(numRows==0){
					//System.out.println("numRows=0");
					updateDB(res->{
						if(res.succeeded()){
							//System.out.println("success in numrows=0");
							next.handle(Future.succeededFuture());
						}
						else
							System.out.println("fail in numrows=0");
					},newest_temp,fut_my,false,-1);
					
					
				}else{
					numRows=sel.result().getNumRows();
					JsonObject a=sel.result().getRows().get(numRows-1); //last row
					int lastTransNum=(int)a.getValue("transNum");
					//System.out.println(portNum+" "+cardNum+"////////////////////////////////////////////////////////"+lastTransNum);
					updateDB(res->{
						if(res.succeeded()){
							//System.out.println("success in numrows=0");
							next.handle(Future.succeededFuture());
						}
						else
							System.out.println("fail in numrows=0");
					},newest_temp,fut_my,true,lastTransNum+1);
					
				}

			}else{
				System.out.println("failed select");
				fut_a.failed();
			}
		});
		
	}

	private void sendToOthers(Handler<AsyncResult<Void>> next, MyCard newest, Future<Void> fut1) {

		SharedData sd=vertx.sharedData();
		LocalMap<String,Buffer> map=sd.getLocalMap("portMap");
		Buffer l=map.get("listOfPorts_pos");
		
		String temp=(l.getString(0,l.length()));
		
		Buffer server=map.get("listOfPorts_server");
		temp=(server.getString(0,server.length()))+" "+temp;
		
		String[] listOfPorts_temp=temp.split(" ");

		int[] listOfPorts=new int[listOfPorts_temp.length];
		for(int i=0;i<listOfPorts_temp.length;i++)
			listOfPorts[i]=Integer.parseInt(listOfPorts_temp[i]);

		HttpClient client=null;
		if(listOfPorts.length>1)
			client=vertx.createHttpClient();
		for(int i=0;i<listOfPorts.length;i++){
			if(listOfPorts[i]==this.portNum)
				continue;

				HttpClientRequest req=client.post(listOfPorts[i],"localhost","/update_pos",new Handler<HttpClientResponse>(){
				public void handle(HttpClientResponse response){
					//System.out.println("Got response back ----"+response.getHeader("status"));
				}
			});


			req.putHeader("sender", Integer.toString(this.portNum));

			
			req.putHeader("cardNum",Integer.toString(newest.getCardNum()));
			req.putHeader("location",newest.getLastTransaction().getLocation());
			req.putHeader("amount",Double.toString(newest.getLastTransaction().getAmount()));
			req.putHeader("date",newest.getLastTransaction().getDate());
			req.putHeader("itemName",newest.getLastTransaction().getItemName());
			req.putHeader("quantity",Integer.toString(newest.getLastTransaction().getQuantity()));
			
			req.end();
			//System.out.println("leave pos sending");


		}
		next.handle(Future.succeededFuture());

	}

	public void updateDB(Handler<AsyncResult<Void>> next,MyCard c ,Future<Void> fut,boolean check,int transNum){
		//System.out.println("in updateDB_initial");

		int lNum=check?transNum:1;
		
		String data=c.getCardNum()+","+lNum+",'"+c.getLastTransaction().getLocation()+"',"+
				c.getLastTransaction().getAmount()+",'"+c.getLastTransaction().getDate()+"','"+c.getLastTransaction().getItemName()
				+"',"+c.getLastTransaction().getQuantity();

		String query="INSERT INTO CBR (id,transNum,location,amount,timeOfTransaction,itemName,quantity) "
				+ "VALUES ("+data+")";

		//System.out.println(portNum);
		//System.out.println(query);

		this.conn.execute(query,ar->{
			if(ar.failed()){
				System.out.println("insert_CBR fail in pos");

				fut.fail(ar.cause());
				return;
			}else{
				//System.out.println("insert_CBR succesfully");

				String findBalance="select balance from Balance_reg where id = "+c.getCardNum();
				this.conn.query(findBalance,rp->{
					if(rp.succeeded()){
						JsonObject b=rp.result().getRows().get(0); 
						Double presentBalance=(Double)(b.getValue("balance"));
						double newBalance=presentBalance-(c.getLastTransaction().getAmount()*c.getLastTransaction().getQuantity());

						String q1="update Balance_reg set balance = "+newBalance+" where id = "+c.getCardNum();

						this.conn.execute(q1,res2->{
							if(res2.succeeded()){
								//System.out.println("updated_balance succesful ");
								temp=new MyCard(c.getCardNum(),newBalance,new MyTransaction(c.getLastTransaction().getLocation()
										,c.getLastTransaction().getAmount(),c.getLastTransaction().getDate(),
										c.getLastTransaction().getItemName(),c.getLastTransaction().getQuantity()));
								next.handle(Future.succeededFuture());
								return;
							}else{
								System.out.println("failed");
								fut.fail(res2.cause());
								return;
							}
						});

					}else{
						System.out.println("select failed");
						fut.fail(rp.cause());
					}
				});

			}
		});
	}

	public void startProcess(Handler<AsyncResult<Void>> next, Future<Void> fut1) throws Exception {

		Future<Void> fut=Future.future();
		establishConnection((connection)-> initializeDB(connection,init->{
			createServer(last->{
				if(last.succeeded()){
					System.out.println("finished creating server");
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
