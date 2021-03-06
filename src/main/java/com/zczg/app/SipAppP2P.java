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
//		JDBCUtils.update("update p2puser set state = " + cur_env.getSettingsInt().get("user_offline")
//				+ ", auth = null");
//		JDBCUtils.update("truncate p2psession");
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
		
		if(userto != null && userfrom != null)
		{
			Integer toState = (Integer)userto.get("state");
			if(toState.equals(cur_env.getSettingsInt().get("user_idle")))
			{
				Address from = sipFactory.createAddress("sip:" + userfrom.get("name") + "@" + userfrom.get("ip") + ":" 
							+ userfrom.get("port"));
				Address to = sipFactory.createAddress("sip:" + userto.get("name") + "@" + userto.get("ip") + ":" 
						+ userto.get("port"));
				
				SipSession session = request.getSession();
				SipServletRequest invite = sipFactory.createRequest(request.getApplicationSession(), "INVITE", session.getRemoteParty(), to);
				
				invite.setContent(request.getContent(), request.getContentType());
				
				session.setAttribute("LinkedSession", invite.getSession());
				invite.getSession().setAttribute("LinkedSession", session);
				session.setAttribute("originalRequest", request);
				invite.getSession().setAttribute("originalRequest", invite);
				
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
    protected void doCancel(SipServletRequest request)
			throws ServletException, IOException {
		logger.info("Got CANCEL: \n" + request.toString());
		
		SipSession linkedSession = (SipSession) request.getSession().getAttribute("LinkedSession");
		if(linkedSession != null)
		{
			SipServletRequest origin = (SipServletRequest) linkedSession.getAttribute("originalRequest");
			SipServletRequest cancel = origin.createCancel();
			cancel.send();
			logger.info(cancel.toString());
		}
		
		SipServletResponse ok = request.createResponse(SipServletResponse.SC_OK);
		ok.send();
	}
	
	@Override
    protected void doProvisionalResponse(SipServletResponse resp)
			throws ServletException, IOException {
		logger.info("Got RESPONSE: \n" + resp.toString());
		
		SipSession linkedSession = (SipSession) resp.getSession().getAttribute("LinkedSession");
		if(linkedSession != null)
		{
			SipServletRequest request = (SipServletRequest) linkedSession.getAttribute("originalRequest");
			SipServletResponse re = request.createResponse(resp.getStatus());
			re.send();
			logger.info(re.toString());
		}
	}
	
	@Override
    protected void doErrorResponse(SipServletResponse resp)
			throws ServletException, IOException {
		logger.info("Got ERROR: \n" + resp.toString());
		
		SipSession linkedSession = (SipSession) resp.getSession().getAttribute("LinkedSession");
		if(linkedSession != null)
		{
			SipServletRequest request = (SipServletRequest) linkedSession.getAttribute("originalRequest");
			SipServletResponse re = request.createResponse(resp.getStatus());
			re.send();
			logger.info(re.toString());
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

			SipSession linkedSession = (SipSession) resp.getSession().getAttribute("LinkedSession");
			if(linkedSession != null)
			{
				SipServletRequest request = (SipServletRequest) linkedSession.getAttribute("originalRequest");
				SipServletResponse re = request.createResponse(resp.getStatus());
				re.setContent(resp.getContent(), resp.getContentType());
				re.send();
				logger.info(re.toString());
			}
			
			SipServletRequest ack = resp.createAck();
			ack.send();
		}
	}

	@Override
	protected void doBye(SipServletRequest request) throws ServletException,
			IOException {
		SipServletResponse sipServletResponse = request.createResponse(SipServletResponse.SC_OK);
		sipServletResponse.send();
		
		SipSession linkedSession = (SipSession) request.getSession().getAttribute("LinkedSession");
		if(linkedSession != null)
		{
			SipServletRequest bye = linkedSession.createRequest("BYE");
			bye.send();
			logger.info(bye.toString());
		}
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
