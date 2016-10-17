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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.URI;
import javax.servlet.sip.Address;
import javax.servlet.sip.SipFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;

import com.zczg.app.*;
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
		
		//TO DO init db
		JDBCUtils.update("update p2puser set state = " + cur_env.getSettingsInt().get("user_offline")
				+ ", auth = null");
		JDBCUtils.update("truncate p2psession");
	}

	@Override
	protected void doInvite(SipServletRequest request) throws ServletException,
			IOException {

		logger.info("Got INVITE: " + request.toString());
		
		SipServletResponse ring = request.createResponse(SipServletResponse.SC_RINGING);
		ring.send();
		
		String fromUri = request.getFrom().getURI().toString();
		String toUri = request.getTo().getURI().toString();
		
		logger.debug("Call from " + fromUri + " to " + toUri);
		
		SipServletResponse busy = request.createResponse(486);
		busy.send();
	}
	
	@Override
	protected void doAck(SipServletRequest request)
			throws ServletException, IOException {

		logger.info("Got: \n" + request.toString());
	}
	
	@Override
    protected void doSuccessResponse(SipServletResponse resp)
			throws ServletException, IOException {
		logger.info("Got OK");
		SipSession session = resp.getSession();

		String cSeqValue = resp.getHeader("CSeq");
		if(cSeqValue.indexOf("INVITE") != -1) {				
			logger.info("Got OK from second party -- sending ACK");

			SipServletRequest secondPartyAck = resp.createAck();
			SipServletRequest firstPartyOk = (SipServletRequest) resp
					.getSession().getAttribute("FirstPartyOk");

//					if (resp.getContentType() != null && resp.getContentType().equals("application/sdp")) {
				firstPartyOk.setContent(resp.getContent(),
						"application/sdp");
				secondPartyAck.setContent(resp.getSession().getAttribute("FirstPartyContent"),
						"application/sdp");
//					}

			firstPartyOk.send();
			secondPartyAck.send();
		}
	}

	@Override
	protected void doBye(SipServletRequest request) throws ServletException,
			IOException {
		SipServletResponse sipServletResponse = request.createResponse(SipServletResponse.SC_OK);
		sipServletResponse.send();	
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
		
		String from = req.getFrom().toString();
		String username = from.substring(from.indexOf("sip:") + 4, from.indexOf("@"));
		String ip = from.substring(from.indexOf("@") + 1, from.indexOf(">;tag="));
		logger.info("User " + username + " ip " + ip);
		Map<String, Object> user =  JDBCUtils.queryForMap("select * from p2puser where name = '" + username + "'");
		
		String auth = req.getHeader("Proxy-Authorization");
		if(auth == null)
		{
			SipServletResponse resp = req.createResponse(SipServletResponse.SC_UNAUTHORIZED);
			
			String nonce = RandomCharUtil.getRandomNumberUpperLetterChar(32);
			resp.addHeader("Proxy-Authenticate", "Digest realm=\"" + cur_env.getSettings().get("realm") + "\""
					+ ",nonce=\"" + nonce + "\",algorithm=MD5");
			JDBCUtils.update("update p2puser set auth = '" + nonce + "' where name = '" + username + "'");

			resp.send();
			logger.info("Request authenticate for " + username);
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
							+ ", ltime = '" + df.format(new Date()) + "' where name = '" + username + "'");
					logger.info("User " + username + " unregistered");
				}
				else{
					SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					String now = df.format(new Date());
					JDBCUtils.update("update p2puser set state = "+ cur_env.getSettingsInt().get("user_idle")
							+ ", ltime = '" + df.format(new Date()) + "' where name = '" + username + "'");
					logger.info("User " + username + " registered");
				}
				
				resp.send();
			}
			else{
				SipServletResponse resp = req.createResponse(SipServletResponse.SC_FORBIDDEN);
				logger.info("User " + username + " registered fail");
				resp.send();
			}
		}
		// TODO multi device
	}
}
