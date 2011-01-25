/*
 * Copyright (C) 2010 Pavel Stastny
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.incad.Kramerius.security.rightscommands.get;

import static cz.incad.utils.IKeys.UUID_PARAMETER;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.antlr.stringtemplate.StringTemplate;

import cz.incad.Kramerius.security.ServletCommand;
import cz.incad.Kramerius.security.rightscommands.ServletRightsCommand;
import cz.incad.Kramerius.security.strenderers.CriteriumParamsWrapper;
import cz.incad.Kramerius.security.strenderers.CriteriumWrapper;
import cz.incad.Kramerius.security.strenderers.RightWrapper;
import cz.incad.kramerius.security.Right;
import cz.incad.kramerius.security.RightCriteriumParams;
import cz.incad.kramerius.security.impl.criteria.CriteriumsLoader;

/**
 * Ziskani javascriptu pri zobrazeni editacniho forumare
 */
public class EditRightsJSData extends ServletRightsCommand {

    static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(EditRightsJSData.class.getName());
    
    @Override
    public void doCommand() {
        try {
            HttpServletRequest req = this.requestProvider.get();
            HttpServletResponse resp = this.responseProvider.get();
            String rightId = req.getParameter("rightid");
            Right right = new RightWrapper(fedoraAccess, rightsManager.findRightById(Integer.parseInt(rightId)));
            
            StringTemplate template = ServletRightsCommand.stJSDataGroup().getInstanceOf("editRightData");
            RightCriteriumParams[] allParams = rightsManager.findAllParams();
            template.setAttribute("allParams", CriteriumParamsWrapper.wrapCriteriumParams(allParams));
            template.setAttribute("allCriteriums", CriteriumWrapper.wrapCriteriums(CriteriumsLoader.criteriums(), true));
            template.setAttribute("right", right);
            template.setAttribute("criterium", right.getCriterium());
            template.setAttribute("criteriumParams", right.getCriterium() != null ? right.getCriterium().getCriteriumParams() : null);
            
            String content = template.toString();
            resp.getOutputStream().write(content.getBytes("UTF-8"));
        } catch (NumberFormatException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(),e);
        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(),e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(),e);
        } catch (InstantiationException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(),e);
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(),e);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(),e);
        }
        
    }


    
}
