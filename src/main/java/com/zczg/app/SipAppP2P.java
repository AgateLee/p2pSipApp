/*
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.zczg.app;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.Address;
import javax.servlet.sip.SipFactory;

import org.apache.log4j.Logger;

import com.zczg.util.CurEnv;
import com.zczg.util.JDBCUtils;
import com.zczg.util.RandomCharUtil;

public class SipAppP2P extends SipServlet {
	
	private static Logger logger = Logger.getLogger(SipAppP2P.class);
	private SipFactory sipFactory;
	private static final String CONTACT_HEADER = "Contact";
	private CurEnv cur_env = new CurEnv();
	
	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		super.init(servletConfig);
		logger.info("the p2pSipApp has been started");
		sipFactory = (SipFactory)getServletContext().getAttribute("javax.servlet.sip.SipFactory");
		//TO DO init db
		JDBCUtils.update("update p2puser set state = " + cur_env.getSettingsInt().get("user_offline")
				+ ", auth = null");
		JDBCUtils.update("truncate p2psession");
	}

	@Override
	protected void doInvite(SipServletRequest request) throws ServletException,
			IOException {

		logger.info("Got INVITE: " + request.toString());
		
		String fromUri = request.getFrom().getURI().toString();
		String toUri = request.getTo().getURI().toString();
		Map<String, Object> userfrom =  JDBCUtils.queryForMap("select * from p2puser where sipname = '" + fromUri + "'");
		Map<String, Object> userto =  JDBCUtils.queryForMap("select * from p2puser where sipname = '" + toUri + "'");
		
		logger.info("Call from " + fromUri + " to " + toUri);
		
		if(!userto.equals(null) && !userfrom.equals(null))
		{
			Integer toState = (Integer)userto.get("state");
			if(toState.equals(cur_env.getSettingsInt().get("user_idle")))
			{
				Address from = sipFactory.createAddress("sip:" + userfrom.get("name") + "@" + userfrom.get("ip") + ":" 
							+ userfrom.get("port"));
				Address to = sipFactory.createAddress("sip:" + userto.get("name") + "@" + userto.get("ip") + ":" 
						+ userto.get("port"));
				SipServletRequest invite = sipFactory.createRequest(request.getSession().getApplicationSession(), "INVITE", from, to);
				
				invite.setContent(request.getContent(), request.getContentType());
				
				SipServletRequest Bye = request.getSession().createRequest("BYE");
				Bye.setHeader("From", request.getFrom().toString());
				Bye.setHeader("To", request.getTo().toString());
				
				cur_env.getTemp().put(invite.getCallId() + "ring", request.createResponse(SipServletResponse.SC_RINGING));
				cur_env.getTemp().put(invite.getCallId() + "ok", request.createResponse(SipServletResponse.SC_OK));
				cur_env.getTemp().put(invite.getCallId() + "bye", Bye);
				cur_env.getTemp().put(invite.getCallId() + "pass", request.getCallId());
				invite.send();
			}
			else if(toState.equals(cur_env.getSettingsInt().get("user_busy")))
			{
				SipServletResponse busy = request.createResponse(SipServletResponse.SC_BUSY_HERE);
				busy.send();
			}
			else
			{
				SipServletResponse notfound = request.createResponse(SipServletResponse.SC_NOT_FOUND);
				notfound.send();
			}
		}
		else
		{
			SipServletResponse notfound = request.createResponse(SipServletResponse.SC_NOT_FOUND);
			notfound.send();
		}
	}
	
	@Override
    protected void doProvisionalResponse(SipServletResponse resp)
			throws ServletException, IOException {
		logger.info("Got RESPONSE: \n" + resp.toString());
		
		if(((Integer)resp.getStatus()).equals(SipServletResponse.SC_RINGING))
		{
			SipServletResponse ring = (SipServletResponse)cur_env.getTemp().get(resp.getCallId() + "ring");
			ring.send();
			cur_env.getTemp().remove(resp.getCallId() + "ring");
			
			SipServletRequest Bye = resp.getSession().createRequest("BYE");
			Bye.setHeader("From", resp.getFrom().toString());
			Bye.setHeader("To", resp.getTo().toString());
			cur_env.getTemp().put(cur_env.getTemp().get(resp.getCallId() + "pass") + "bye", Bye);
		}
	}
	
	@Override
	protected void doAck(SipServletRequest request)
			throws ServletException, IOException {

		logger.info("Got ACK: \n" + request.toString());
	}
	
	@Override
    protected void doSuccessResponse(SipServletResponse resp)
			throws ServletException, IOException {
		logger.info("Got OK");
		
		String cSeqValue = resp.getHeader("CSeq");
		if(cSeqValue.indexOf("INVITE") != -1) {				
			SipServletResponse ok = (SipServletResponse)cur_env.getTemp().get(resp.getCallId() + "ok");
			
			ok.setContent(resp.getContent(), resp.getContentType());
			ok.send();
			cur_env.getTemp().remove(resp.getCallId() + "ok");
			
			SipServletRequest ack = resp.createAck();
			ack.send();
		}
	}

	@Override
	protected void doBye(SipServletRequest request) throws ServletException,
			IOException {
		SipServletResponse sipServletResponse = request.createResponse(SipServletResponse.SC_OK);
		sipServletResponse.send();
		SipServletRequest Bye = (SipServletRequest) cur_env.getTemp().get(request.getCallId() + "bye");
		Bye.send();
		
		cur_env.getTemp().clear();
	}
	
//	@Override
//	protected void doSubscribe(SipServletRequest request) throws ServletException,
//			IOException {
//		// DO NOTHING
//	}
	
	@Override
	protected void doRegister(SipServletRequest req) throws ServletException, IOException {
		logger.info("Received register request: " + req.getTo());
		logger.info(req.toString());
		
		String from = req.getFrom().getURI().toString();
		String contact = req.getHeader("Contact");
		String[] ss = contact.split("[@:;]");
		String username = ss[1];
		String ip = ss[2];
		String port = ss[3];
		logger.info("User " + username + " ip " + ip + ":" + port);
		
		String auth = req.getHeader("Proxy-Authorization");
		if(auth == null)
		{
			SipServletResponse resp = req.createResponse(SipServletResponse.SC_UNAUTHORIZED);
			
			String nonce = RandomCharUtil.getRandomNumberUpperLetterChar(32);
			resp.addHeader("Proxy-Authenticate", "Digest realm=\"" + cur_env.getSettings().get("realm") + "\""
					+ ",nonce=\"" + nonce + "\",algorithm=MD5");
			JDBCUtils.update("update p2puser set sipname = '" + from + "', name = '" + username + "', ip = '"+ ip
					+ "', port = '" + port + "', auth = '" + nonce + "' where sipname = '" + from + "'");

			resp.send();
			logger.info("Request authenticate for " + from);
		}
		else
		{
			Map<String,Object> map = JDBCUtils.queryForMap("select * from p2puser where name = '" + username + "'");
			int st = auth.indexOf("response=\"") + 10;
			int ed = auth.indexOf("\"", st);
			String digest = auth.substring(st, ed);
			st = auth.indexOf("uri=\"") + 5;
			ed = auth.indexOf("\"", st);
			String uri = auth.substring(st, ed);
			String method = req.getMethod();
			
			String check = cur_env.myDigest(username, cur_env.getSettings().get("realm"), 
					(String)map.get("passwd"), (String)map.get("auth"), method, uri);
			
			if(digest.equals(check))
			{
				SipServletResponse resp = req.createResponse(SipServletResponse.SC_OK);
				Address address = req.getAddressHeader(CONTACT_HEADER);
				resp.setAddressHeader(CONTACT_HEADER, address);
				
				int expires = address.getExpires();
				if(expires < 0) {
					expires = req.getExpires();
				}
				
				if(expires == 0) {
					SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					JDBCUtils.update("update p2puser set auth = null, state = "+ cur_env.getSettingsInt().get("user_offline")
							+ ", ltime = '" + df.format(new Date()) + "' where sipname = '" + from + "'");
					logger.info("User " + from + " unregistered");
				}
				else{
					SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					JDBCUtils.update("update p2puser set state = "+ cur_env.getSettingsInt().get("user_idle")
							+ ", ltime = '" + df.format(new Date()) + "' where sipname = '" + from + "'");
					logger.info("User " + from + " registered");
				}
				
				resp.send();
			}
			else{
				SipServletResponse resp = req.createResponse(SipServletResponse.SC_FORBIDDEN);
				logger.info("User " + from + " registered fail");
				resp.send();
			}
		}
		// TODO multi device
	}
}
