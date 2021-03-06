  /***************************************************************************
*                                                                          *
*  Organization: Lawrence Livermore National Lab (LLNL)                    *
*   Directorate: Computation                                               *
*    Department: Computing Applications and Research                       *
*      Division: S&T Global Security                                       *
*        Matrix: Atmospheric, Earth and Energy Division                    *
*       Program: PCMDI                                                     *
*       Project: Earth Systems Grid Federation (ESGF) Data Node Software   *
*  First Author: Gavin M. Bell (gavin@llnl.gov)                            *
*                                                                          *
****************************************************************************
*                                                                          *
*   Copyright (c) 2009, Lawrence Livermore National Security, LLC.         *
*   Produced at the Lawrence Livermore National Laboratory                 *
*   Written by: Gavin M. Bell (gavin@llnl.gov)                             *
*   LLNL-CODE-420962                                                       *
*                                                                          *
*   All rights reserved. This file is part of the:                         *
*   Earth System Grid Federation (ESGF) Data Node Software Stack           *
*                                                                          *
*   For details, see http://esgf.org/esg-node/                             *
*   Please also read this link                                             *
*    http://esgf.org/LICENSE                                               *
*                                                                          *
*   * Redistribution and use in source and binary forms, with or           *
*   without modification, are permitted provided that the following        *
*   conditions are met:                                                    *
*                                                                          *
*   * Redistributions of source code must retain the above copyright       *
*   notice, this list of conditions and the disclaimer below.              *
*                                                                          *
*   * Redistributions in binary form must reproduce the above copyright    *
*   notice, this list of conditions and the disclaimer (as noted below)    *
*   in the documentation and/or other materials provided with the          *
*   distribution.                                                          *
*                                                                          *
*   Neither the name of the LLNS/LLNL nor the names of its contributors    *
*   may be used to endorse or promote products derived from this           *
*   software without specific prior written permission.                    *
*                                                                          *
*   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS    *
*   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT      *
*   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS      *
*   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL LAWRENCE    *
*   LIVERMORE NATIONAL SECURITY, LLC, THE U.S. DEPARTMENT OF ENERGY OR     *
*   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,           *
*   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT       *
*   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF       *
*   USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND    *
*   ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,     *
*   OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT     *
*   OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF     *
*   SUCH DAMAGE.                                                           *
*                                                                          *
***************************************************************************/
package esg.node.components.registry;

import esg.common.generated.registration.*;
import esg.common.util.ESGFProperties;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.util.Properties;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.*;

import static esg.node.components.registry.NodeTypes.*;

/**
   Description:
   
   Encapsulates the logic for fetching and generating this local
   node's LAS Sisters file as defined by las_servers.xsd

*/
public class LasSistersGleaner {

    private static final Log log = LogFactory.getLog(LasSistersGleaner.class);

    private LasServers servers = null;
    private String sistersFile = "las_servers.xml";
    private String sistersPath = null;
    private Properties props = null;
    private String defaultLocation = null;
    private ExclusionListReader.ExclusionList exList = null;

    public LasSistersGleaner() { this(null); }
    public LasSistersGleaner(Properties props) {
        try {
            if(props == null) this.props = new ESGFProperties();
            else this.props = props;

            String base = System.getenv("ESGF_HOME");
            if (base != null) {
                defaultLocation = base+"/content/las/conf/server";
            }else {
                defaultLocation = "/tmp";
                log.warn("ESGF_HOME environment var not set!");
            }
            sistersPath = props.getProperty("las.xml.config.dir",defaultLocation)+File.separator;
            exList = ExclusionListReader.getInstance().getExclusionList().useType(COMPUTE_BIT);
            servers = new LasServers();

        } catch(Exception e) {
            log.error(e);
        }
    }
    
    public LasServers getMyLasServers() { return servers; }
    
    public boolean saveLasServers() { return saveLasServers(servers); }
    public synchronized boolean saveLasServers(LasServers servers) {
        boolean success = false;
        if (servers == null) {
            log.error("LasServers is ["+servers+"]"); 
            return success;
        }
        log.trace("Saving LAS LasServers information to "+sistersPath+sistersFile);
        try{
            JAXBContext jc = JAXBContext.newInstance(LasServers.class);
            Marshaller m = jc.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            m.marshal(servers, new FileOutputStream(sistersPath+sistersFile));
            success = true;
        }catch(Exception e) {
            log.error(e);
        }
	
        return success;
    }

    
    /**
       Looks through the current system and gathers the configured
       node service information.  Takes that information and
       creates a local representation of this node's registration.
    */
    public synchronized LasSistersGleaner appendToMyLasServersFromRegistration(Registration registration) {
        log.trace("Creating my LAS LasServers representation...");
        log.trace("Registration is ["+registration+"]");
        try{
            LASService service = null; //the LASService entry from the registration -via-> node
            LasServer sister = null;   //Local servers xml element

            //NOTE: Entries stored in the registration are dedup'ed so no worries here ;-)
            int numNodes = registration.getNode().size();
            int lasNodes = 0;
            log.trace("Registration has ("+numNodes+") nodes");
            for(Node node : registration.getNode()) {
                //TODO - put in sanity check for nodeType integrity
                service = node.getLASService();
                if (null == service) {
                    log.trace(node.getShortName()+" skipping... does not run an LAS service.");
                    continue;
                }
                if(exList.isExcluded(node.getHostname())) {
                    log.trace(node.getHostname()+" skipping... found in excludes list!!");
                    continue;
                }
                sister = new LasServer();
                sister.setName(node.getShortName());
                sister.setUrl(service.getEndpoint());
                sister.setIp(node.getIp());
                servers.getLasServer().add(sister);
                lasNodes++;
            }
            log.trace(lasNodes+" of "+numNodes+" gleaned");
        } catch(Exception e) {
            log.error(e);
            e.printStackTrace();
        }
        
        return this;
    }

    public LasSistersGleaner clear() {
        if(this.servers != null) this.servers = new LasServers();
        return this;
    }

    public synchronized LasSistersGleaner loadMyLasServers() {
        log.info("Loading my LAS LasServers info from "+sistersPath+sistersFile);
        try{
            JAXBContext jc = JAXBContext.newInstance(LasServers.class);
            Unmarshaller u = jc.createUnmarshaller();
            JAXBElement<LasServers> root = u.unmarshal(new StreamSource(new File(sistersPath+sistersFile)),LasServers.class);
            servers = root.getValue();
        }catch(Exception e) {
            log.error(e);
        }
        return this;
    }

    //****************************************************************
    // Not really used methods but here for completeness
    //****************************************************************

    public LasServers createLasServersFromString(String lasServersContentString) {
        log.info("Loading my LAS LasServers info from \n"+lasServersContentString+"\n");
        LasServers fromContentLasServers = null;
        try{
            JAXBContext jc = JAXBContext.newInstance(LasServers.class);
            Unmarshaller u = jc.createUnmarshaller();
            JAXBElement<LasServers> root = u.unmarshal(new StreamSource(new StringReader(lasServersContentString)),LasServers.class);
            fromContentLasServers = root.getValue();
        }catch(Exception e) {
            log.error(e);
        }
        return fromContentLasServers;
    }

}