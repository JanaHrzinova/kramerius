package cz.incad.Kramerius;

import static cz.incad.utils.IKeys.UUID_PARAMETER;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;
import org.antlr.stringtemplate.language.DefaultTemplateLexer;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import sun.dc.pr.PathStroker;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import cz.incad.Kramerius.HandleServlet.HandleType;
import cz.incad.Kramerius.backend.guice.GuiceServlet;
import cz.incad.Kramerius.security.CurrentLoggedUserProvider;
import cz.incad.kramerius.FedoraAccess;
import cz.incad.kramerius.SolrAccess;
import cz.incad.kramerius.imaging.DeepZoomCacheService;
import cz.incad.kramerius.security.IsActionAllowed;
import cz.incad.kramerius.security.RightCriteriumContextFactory;
import cz.incad.kramerius.security.RightCriteriumException;
import cz.incad.kramerius.security.RightsManager;
import cz.incad.kramerius.security.SecuredActions;
import cz.incad.kramerius.security.SecurityException;
import cz.incad.kramerius.security.SpecialObjects;
import cz.incad.kramerius.security.User;
import cz.incad.kramerius.security.UserManager;
import cz.incad.kramerius.utils.IOUtils;
import cz.incad.kramerius.utils.conf.KConfiguration;
import cz.incad.kramerius.utils.solr.SolrUtils;

public class ViewInfoServlet extends GuiceServlet {

    public static final java.util.logging.Logger LOGGER = java.util.logging.Logger
            .getLogger(MimeTypeServlet.class.getName());
    
    @Inject
    @Named("rawFedoraAccess")
    FedoraAccess fedoraAccess;
    
    @Inject
    SolrAccess solrAccess;
    
    @Inject
    DeepZoomCacheService deepZoomCacheService;

    @Inject
    IsActionAllowed actionAllowed;

    
    @Inject
    RightsManager rightsManager;

    @Inject
    RightCriteriumContextFactory ctxFactory;

    @Inject
    Provider<User> currentLoggedUserProvider;
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String uuid = req.getParameter(UUID_PARAMETER);
            String[] pathOfUUIDs = this.solrAccess.getPathOfUUIDs(uuid);
            
            User user = currentLoggedUserProvider.get();
            System.out.println("user instance 0x"+Integer.toHexString(System.identityHashCode(user)));

            if ((uuid != null) && (!uuid.equals(""))) {
                String mimeType = this.fedoraAccess.getImageFULLMimeType(uuid);
                boolean generated = resolutionFilePresent(uuid);
                boolean conf = deepZoomConfigurationEnabled(uuid);
                boolean hasAlto = this.fedoraAccess.isStreamAvailable(uuid, "ALTO");
                
                HashMap map = new HashMap();
                map.put("deepZoomCacheGenerated", ""+generated);
                map.put("deepZoomCofigurationEnabled", ""+conf);
                map.put("imageServerConfigured", ""+(!KConfiguration.getInstance().getUrlOfIIPServer().equals("")));
                map.put("uuid", uuid);
                map.put("pathOfUuids",pathOfUUIDs);
                
                        
                HashMap<String, HashMap<String, String>> secMapping = new HashMap<String, HashMap<String,String>>(); 
                
                // interpretuj pravo READ pro nynejsi cestu  -
                // standardne jsou zdroje chraneny pres securedFedoraAccess zde je to jine, 
                // aby se pravo ineterpretovalo vicekrat, interpretuje se zde jednou a vysledek 
                // se pak vyhodnoti
                boolean[] vals = fillActionsToJSON(req, uuid, pathOfUUIDs, secMapping, SecuredActions.READ);
                if (!firstMustBeTrue(vals)) {
                    throw new SecurityException("access denided");
                }
                
                // pravo admin do kontext menu
                fillActionsToJSON(req, uuid, pathOfUUIDs, secMapping, SecuredActions.ADMINISTRATE);
                
                map.put("actions",secMapping);
                
                HttpSession session = req.getSession();
                if (session != null) {
                    List<String> actions = (List<String>) session.getAttribute(CurrentLoggedUserProvider.SECURITY_FOR_REPOSITORY_KEY);
                    if (actions != null) {
                        SecuredActions[] acts = new SecuredActions[] {SecuredActions.ADMINISTRATE, SecuredActions.READ};
                        for (SecuredActions act : acts) {
                            actions.remove(act.getFormalName());
                        }
                        map.put("globalActions", actions);
                    } else {
                        map.put("globalActions", null);
                    }
                } else {
                    map.put("globalActions", null);
                }
                
                map.put("mimeType", mimeType);
                map.put("hasAlto", ""+hasAlto);

                
                resp.setContentType("text/plain");
                StringTemplate template = stGroup().getInstanceOf("viewinfo");
                template.setAttribute("data", map);
                
                resp.getWriter().println(template.toString());
            }
        } catch (XPathExpressionException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } catch (SAXException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } catch(SecurityException e) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }


    private boolean firstMustBeTrue(boolean[] vals) {
        return (vals.length > 0) && (vals[0]);
    }
    
    private boolean atLeastOneTrue(boolean[] vals) {
        boolean accessed = false;
        for (boolean val : vals) {
            if (val) {
                accessed = true;
                break;
            }
        }
        return accessed;
    }


    public boolean[] fillActionsToJSON(HttpServletRequest req, String uuid, String[] pathOfUUIDs, HashMap<String, HashMap<String, String>> secMapping,SecuredActions act) {
        ArrayList<String> pathWithRepository = new ArrayList<String>(Arrays.asList(pathOfUUIDs));
        pathWithRepository.add(0, SpecialObjects.REPOSITORY.getUuid());
        Collections.reverse(pathWithRepository);

        boolean[] allowedActionForPath = actionAllowed.isActionAllowedForAllPath(act.getFormalName(), uuid,pathOfUUIDs);
        
        for (int j = 0; j < allowedActionForPath.length; j++) {
            if (!secMapping.containsKey(act.getFormalName())) {
                secMapping.put(act.getFormalName(), new HashMap<String, String>());
            }
            HashMap<String, String> pathMap = secMapping.get(act.getFormalName());
            pathMap.put(pathWithRepository.get(j), ""+allowedActionForPath[j]);
        }
        return allowedActionForPath;
    }
    

    private boolean resolutionFilePresent(String uuid) throws IOException, ParserConfigurationException, SAXException {
        boolean resFile = deepZoomCacheService.isResolutionFilePresent(uuid);
        return resFile;
    }
    
    
    private boolean deepZoomConfigurationEnabled(String uuid) {
        try {
            Document parseDocument = solrAccess.getSolrDataDocumentByUUID(uuid);
            String pidPath = SolrUtils.disectPidPath(parseDocument);
            return KConfiguration.getInstance().isDeepZoomEnabled() || KConfiguration.getInstance().isDeepZoomForPathEnabled(pidPath.split("/"));
        } catch (XPathExpressionException e) {
            LOGGER.severe(e.getMessage());
        } catch (IOException e) {
            LOGGER.severe(e.getMessage());
        }
        return false;
    }
    
    private StringTemplateGroup stGroup() throws IOException {
        InputStream stream = GetRelsExt.class.getResourceAsStream("viewinfo.stg");
        String string = IOUtils.readAsString(stream, Charset.forName("UTF-8"), true);
        StringTemplateGroup group = new StringTemplateGroup(new StringReader(string), DefaultTemplateLexer.class);
        return group;
    }

    
    public static void main(String[] args) {
        StringTemplate template = new StringTemplate(
"$data.keys:{action| $data.(action).keys:{ key| $key$ :  $data.(action).(key)$ };separator=\",\"$ }$") ;

        
        HashMap map = new HashMap();

        HashMap<String, String> data = new HashMap<String, String>(); {
            data.put("drobnustky","true");
            data.put("stranka","true");
            data.put("repository","true");
        };
        map.put("edit",data);
        
        template.setAttribute("data", map);
        System.out.println(template.toString());
        
    }
}
