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
import java.util.HashMap;
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

import com.zczg.app.*;
import com.zczg.util.CurEnv;
import com.zczg.util.JDBCUtils;

public class SipAppP2P extends SipServlet {
	
	private static Log logger = LogFactory.getLog(SipAppP2P.class);
	private SipFactory sipFactory;
	private static final String CONTACT_HEADER = "Contact";
	private CurEnv cur_env = new CurEnv();
	private JDBCUtils db = new JDBCUtils();
	
	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		super.init(servletConfig);
		logger.info("the p2pSipApp has been started");
		
		//TO DO init db
		db.update("update p2puser set state = " + cur_env.getSettingsInt().get("user_offline"));
		db.update("truncate p2psession");
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
	
	protected void doRegister(SipServletRequest req) throws ServletException, IOException {
		logger.info("Received register request: " + req.getTo());

		int response = SipServletResponse.SC_OK;
		SipServletResponse resp = req.createResponse(response);
		HashMap<String, String> users = (HashMap<String, String>) getServletContext().getAttribute("registeredUsersMap");
		if(users == null) users = new HashMap<String, String>();
		getServletContext().setAttribute("registeredUsersMap", users);
		
		Address address = req.getAddressHeader(CONTACT_HEADER);
		String fromURI = req.getFrom().getURI().toString();
		
		int expires = address.getExpires();
		if(expires < 0) {
			expires = req.getExpires();
		}
		if(expires == 0) {
			users.remove(fromURI);
			logger.info("User " + fromURI + " unregistered");
		} else {
			resp.setAddressHeader(CONTACT_HEADER, address);
			users.put(fromURI, address.getURI().toString());
			logger.info("User " + fromURI + 
					" registered with an Expire time of " + expires);
		}				
						
		resp.send();
	}
}
