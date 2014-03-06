package com.uptyc.IcapAdapter;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import com.reuters.rfa.ansipage.Page;
import com.reuters.rfa.ansipage.PageUpdate;
import com.reuters.rfa.common.PublisherPrincipalIdentity;
import com.reuters.rfa.common.QualityOfService;
import com.reuters.rfa.dictionary.DataDef;
import com.reuters.rfa.dictionary.DataDefDictionary;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.ElementEntryDef;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.dictionary.FieldEntryDef;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMDataBuffer;
import com.reuters.rfa.omm.OMMDataDefs;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMEnum;
import com.reuters.rfa.omm.OMMException;
import com.reuters.rfa.omm.OMMFieldEntry;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMFilterList;
import com.reuters.rfa.omm.OMMIterable;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMPriority;
import com.reuters.rfa.omm.OMMSeries;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.omm.OMMVector;
import com.reuters.rfa.omm.OMMVectorEntry;
import com.reuters.rfa.rdm.RDMDictionary;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.utility.HexDump;
import com.thomsonreuters.rfa.valueadd.domainrep.DomainRequest;
import com.thomsonreuters.rfa.valueadd.domainrep.DomainResponse;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.dictionary.RDMDictionaryRequest;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectoryRequest;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectoryRequestAttrib;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectoryResponse;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectoryResponseAttrib;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectoryResponsePayload;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.RDMDirectory;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.Service;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.Service.DataFilter;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.Service.GroupFilter;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.Service.InfoFilter;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.Service.LinkFilter;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.Service.LoadFilter;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.Service.LinkFilter.Link;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.login.RDMLoginRequest;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.login.RDMLoginRequestAttrib;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.login.RDMLoginResponse;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.login.RDMLoginResponseAttrib;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.login.RDMLoginResponsePayload;
import com.thomsonreuters.rfa.valueadd.domainrep.rdm.login.RDMLogin;

/**
 * The GenericOMMParser is used to read and initialize dictionaries and parse
 * any OMM message that is passed to it using the parse() method.
 * 
 * This class is not thread safe due to the static variables. The "CURRENT"
 * variables save state between methods, so another thread cannot change the
 * values. CURRENT_DICTIONARY requires only one FieldDictionary to be used at a
 * time. CURRENT_PAGE requires only one page to be parsed at a time.
 */
public final class GenericOMMParser
{
    private static HashMap<Integer, FieldDictionary> DICTIONARIES = new HashMap<Integer, FieldDictionary>();
    private static FieldDictionary CURRENT_DICTIONARY;
    private static Page CURRENT_PAGE;

    private static boolean INTERNAL_DEBUG = false;
    private static OMMPool pool = OMMPool.create();

    // Name Tag
    static String tHeader = "MESSAGE";
    static String tDomainType = "\tMessageModelType : ";
    static String tReqMsgType = "\tInteractionType : ";
    static String tRespMsgType = "\tRespType : ";
    static String tInd = "\tIndicationMask : ";
    static String tAttrib = "\tAttribInfo : ";
    static String tAttribE = "\tAttribInfo.Attrib : ";
    static String tRespType = "\tResponseTypeNum : ";
    static String tSeqnum = "\tSeqNum : ";
    static String tSecSeqnum = "\tSecondSeqNum : ";
    static String tStatus = "\tStatus : ";
    static String tGroup = "\tItem Group : ";
    static String tPayload = "\tPayload : ";

    /**
     * This method should be called one before parsing and data.
     * 
     * @param fieldDictionaryFilename
     * @param enumDictionaryFilename
     * @throws DictionaryException if an error has occurred
     */
    public static void initializeDictionary(String fieldDictionaryFilename,
            String enumDictionaryFilename) throws DictionaryException
    {
        FieldDictionary dictionary = FieldDictionary.create();
        try
        {
            FieldDictionary.readRDMFieldDictionary(dictionary, fieldDictionaryFilename);
            System.out.println("field dictionary read from RDMFieldDictionary file");

            FieldDictionary.readEnumTypeDef(dictionary, enumDictionaryFilename);
            System.out.println("enum dictionary read from enumtype.def file");

            initializeDictionary(dictionary);
        }
        catch (DictionaryException e)
        {
            throw new DictionaryException("ERROR: Check if files " + fieldDictionaryFilename
                    + " and " + enumDictionaryFilename + " exist and are readable.", e);
        }
    }

    // This method can be used to initialize a downloaded dictionary
    public synchronized static void initializeDictionary(FieldDictionary dict)
    {
        int dictId = dict.getDictId();
        if (dictId == 0)
            dictId = 1; // dictId == 0 is the same as dictId 1
        DICTIONARIES.put(new Integer(dictId), dict);
    }

    public static FieldDictionary getDictionary(int dictId)
    {
        if (dictId == 0)
            dictId = 1;
        return (FieldDictionary)DICTIONARIES.get(new Integer(dictId));
    }

    public static final void parse(DomainRequest request)
    {
        OMMMsg msg = request.getMsg(pool);
        parse(msg);
        pool.releaseMsg(msg);
    }

    public static final void parse(DomainResponse response)
    {
        System.out.println(" " + response.getDomainType());
        OMMMsg msg = response.getMsg(pool);
        parse(msg);
        pool.releaseMsg(msg);
    }

    public static final void parse(DomainRequest request, PrintStream ps)
    {
        OMMMsg msg = request.getMsg(pool);
        parseMsg(msg, ps);
        pool.releaseMsg(msg);
    }

    public static final void parse(DomainResponse response, PrintStream ps)
    {
        OMMMsg msg = response.getMsg(pool);
        parseMsg(msg, ps);
        pool.releaseMsg(msg);
    }

    public static final void parse(RDMLoginResponse response)
    {
        System.out.println(tHeader);
        System.out.println(tDomainType + response.getDomainType());

        // MessageType
        System.out.print(tRespMsgType);
        System.out.println(response.getMessageType());
        
        // ResponseTypeNum
        System.out.print(tRespType);
        if (response.hasIsRefreshSolicited())
        {
            System.out.println(response.getIsRefreshSolicited() ? "SOLICITED" : "UNSOLICITED");
        }
        else
        {
            System.out.println("None");
        }

        // Status Response
        System.out.print(tStatus);
        if (response.hasRespStatus())
        {
            System.out.println(response.getRespStatus());
        }
        else
        {
            System.out.println("None");
        }

        // IndicationFlag
        System.out.print(tInd);
        if (!response.getIndicationMask().isEmpty())
        {
            for (RDMLoginResponse.IndicationMask indMask : response.getIndicationMask())
            {
                if (indMask == RDMLoginResponse.IndicationMask.CLEAR_CACHE)
                    System.out.print("CLEAR_CACHE ");
                if (indMask == RDMLoginResponse.IndicationMask.REFRESH_COMPLETE)
                    System.out.print("REFRESH_COMPLETE ");
            }
            System.out.println();
        }
        else
        {
            System.out.println("None");
        }

        // Header Not used

        // Sequence Number
        System.out.print(tSeqnum);
        if (response.hasSequenceNum())
        {
            System.out.println(response.getSequenceNum());
        }
        else
        {
            System.out.println("None");
        }
        System.out.print(tSecSeqnum);
        if (response.hasSecondarySequenceNum())
        {
            System.out.println(response.getSecondarySequenceNum());
        }
        else
        {
            System.out.println("None");
        }

        // ConflatedCount not used
        // ConflatedTime not used
        // Item Group not used
        // Permission Data not used

        /**
         * AttribInfo
         * 
         */
        System.out.println(tAttrib);
        if (response.hasAttrib())
        {
            RDMLoginResponseAttrib attrib = response.getAttrib();

            // Data Mask not used

            // Name
            // Name Type
            if (attrib.hasName())
            {
                System.out.println("\t\tName : " + attrib.getName() + " (" + attrib.getNameType()
                        + ")");
            }

            // ServiceName not used
            // ServiceId not used
            // ID not used
            // AttribInfo.Attrib
            System.out.println("\tAttribInfo.Attrib");
            if (attrib.hasAllowSuspectData())
            {
                System.out.println("\t\tAllowSuspectData : " + attrib.getAllowSuspectData());
            }
            if (attrib.hasApplicationId())
            {
                System.out.println("\t\tApplicationId : " + attrib.getApplicationId());
            }
            if (attrib.hasApplicationName())
            {
                System.out.println("\t\tApplicationName : " + attrib.getApplicationName());
            }
            if (attrib.hasPosition())
            {
                System.out.println("\t\tPosition : " + attrib.getPosition());
            }
            if (attrib.hasProvidePermissionExpressions())
            {
                System.out.println("\t\tProvidePermissionExpressions : "
                        + attrib.getProvidePermissionExpressions());
            }
            if (attrib.hasProvidePermissionProfile())
            {
                System.out.println("\t\tProvidePermissionProfile : "
                        + attrib.getProvidePermissionProfile());
            }
            if (attrib.hasSingleOpen())
            {
                System.out.println("\t\tSingleOpen : " + attrib.getSingleOpen());
            }
            if (attrib.hasBatchSupport())
            {
                System.out.println("\t\tSupportBatchReq : "
                        + attrib.getBatchSupport().contains(RDMLogin.SupportBatchRequests.REQUEST_SUPPORTED));
            }
            if (attrib.hasEnhancedSymbolListSupport())
            {
                System.out.println("\t\tSupportEnhancedSymbolList : "
                        + attrib.getEnhancedSymbolListSupport().contains(RDMLogin.SupportEnhancedSymbolList.DATA_STREAMS_SUPPORTED));
            }
            if (attrib.hasOptimizedPauseResumeSupport())
            {
                System.out.println("\t\tSupportOPAR : "
                        + attrib.getOptimizedPauseResumeSupport()
                                .contains(RDMLogin.SupportOptimizedPauseResume.SUPPORTED));
            }
            if (attrib.hasSupportOMMPost())
            {
                System.out.println("\t\tSupportPost : "
                        + attrib.getSupportOMMPost().contains(RDMLogin.SupportOMMPost.SUPPORTED));
            }
            if (attrib.hasStandbySupport())
            {
                System.out.println("\t\tSupportStandby : "
                        + attrib.getStandbySupport().contains(RDMLogin.SupportStandby.SUPPORTED));
            }
            if (attrib.hasViewSupport())
            {
                System.out.println("\t\tSupportViewReq : "
                        + attrib.getViewSupport().contains(RDMLogin.SupportViewRequests.SUPPORTED));
            }
        }
        else
        {
            System.out.println("None");
        }
        // End Attrib Info

        // **** Payload - NOT FINISH YET
        System.out.print(tPayload);
        if (response.hasPayload())
        {
            System.out.println();
            RDMLoginResponsePayload payload = response.getPayload();
            // Not Finish yet
            System.out.print("\t\tConnectionConfig : ");
            if (payload.hasConnectionConfig())
            {
                System.out.println();
                RDMLoginResponsePayload.ConnectionConfig conConfig = payload.getConnectionConfig();
                System.out.print("\t\t\tNumStandbyServers : ");
                if (conConfig.hasNumStandbyServers())
                {
                    System.out.println();
                    conConfig.getNumStandbyServers();
                }
                else
                {
                    System.out.println("None");
                }
                if (conConfig.hasServerList())
                {
                    RDMLoginResponsePayload.ServerList sList = conConfig.getServerList();
                    System.out.print("\t\t\tServerList : ");
                    if (!sList.isEmpty())
                    {
                        for (RDMLoginResponsePayload.Server server : sList)
                        {
                            System.out.println();
                            System.out.print("\t\t\t\tHostname : ");
                            if (server.hasHostName())
                            {
                                System.out.println(server.getHostName());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tPort : ");
                            if (server.hasPort())
                            {
                                System.out.println(server.getPort());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tLoadFactor : ");
                            if (server.hasLoadFactor())
                            {
                                System.out.println(server.getLoadFactor());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tServerType : ");
                            if (server.hasType())
                            {
                                System.out.println(server.getType());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tSystemID : ");
                            if (server.hasSystemId())
                            {
                                System.out.println(server.getSystemId());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                        }
                    }
                }
                else
                {
                    System.out.println("None");
                }
            }
            else
            {
                System.out.println("None");
            }
        }
        else
        {
            System.out.println("None");
        }
    }

    @SuppressWarnings("deprecation")
    public static final void parse(RDMLoginRequest request)
    {

        System.out.println(tHeader);
        System.out.println(tDomainType + request.getDomainType());

        // MessageType
        System.out.print(tReqMsgType);
        System.out.println(request.getMessageType());
       
        // Indication Flags
        System.out.print(tInd);
        if (!request.getIndicationMask().isEmpty())
        {
            for (RDMLoginRequest.IndicationMask indMask : request.getIndicationMask())
            {
                if (indMask == RDMLoginRequest.IndicationMask.REFRESH)
                    System.out.print("REFRESH ");
                if (indMask == RDMLoginRequest.IndicationMask.PAUSE_REQ)
                    System.out.print("PAUSE_REQ ");
                if (indMask == RDMLoginRequest.IndicationMask.RESUME_REQ)
                    System.out.print("RESUME_REQ ");
            }
            System.out.println();
        }
        else
        {
            System.out.println("None");
        }

        // QOS not used
        // Priority not used
        // Header not used

        // AttribInfo
        System.out.println(tAttrib);
        if (request.hasAttrib())
        {
            RDMLoginRequestAttrib attrib = request.getAttrib();

            // Data Mask not used

            // Name and NameType
            if (attrib.hasName())
            {
                System.out.println("\t\tName : " + attrib.getName() + " (" + attrib.getNameType()
                        + ")");
            }

            // ServiceName not used
            // ServiceId not used
            // ID not used

            // AttribInfo.Attrib
            System.out.print(tAttribE);
            String temp = "";
            if (attrib.hasAllowSuspectData())
            {
                // System.out.println("\t\tAllowSuspectData : "+attrib.getAllowSuspectData());
                temp = temp + "\n\t\tAllowSuspectData : " + attrib.getAllowSuspectData();
            }
            if (attrib.hasApplicationId())
            {
                // System.out.println("\t\tApplicationId : "+attrib.getApplicationId());
                temp = temp + "\n\t\tApplicationId : " + attrib.getApplicationId();
            }
            if (attrib.hasApplicationName())
            {
                // System.out.println("\t\tApplicationName : "+attrib.getApplicationName());
                temp = temp + "\n\t\tApplicationName : " + attrib.getApplicationName();
            }
            if (attrib.hasDownloadStandbyConfig())
            {
                // System.out.println("\t\tDownloadConnectionConfig : "+attrib.getDownloadConnectionConfig());
                temp = temp + "\n\t\tDownloadConnectionConfig : "
                        + attrib.getDownloadConnectionConfig();
            }
            if (attrib.hasInstanceId())
            {
                // System.out.println("\t\tInstanceId : "+attrib.getInstanceId());
                temp = temp + "\n\t\tInstanceId : " + attrib.getInstanceId();
            }
            if (attrib.hasPassword())
            {
                // System.out.println("\t\tPassword : "+attrib.getPassword());
                temp = temp + "\n\t\tPassword : " + attrib.getPassword();
            }
            if (attrib.hasPosition())
            {
                // System.out.println("\t\tPosition : "+attrib.getPosition());
                temp = temp + "\n\t\tPosition : " + attrib.getPosition();
            }
            if (attrib.hasProvidePermissionExpressions())
            {
                // System.out.println("\t\tProvidePermissionExpressions : "+attrib.getProvidePermissionExpressions());
                temp = temp + "\n\t\tProvidePermissionExpressions : "
                        + attrib.getProvidePermissionExpressions();
            }
            if (attrib.hasProvidePermissionProfile())
            {
                // System.out.println("\t\tProvidePermissionProfile : "+attrib.getProvidePermissionProfile());
                temp = temp + "\n\t\tProvidePermissionProfile : "
                        + attrib.getProvidePermissionProfile();
            }
            if (attrib.hasRole())
            {
                // System.out.println("\t\tRole : "+attrib.getRole());
                temp = temp + "\n\t\tRole : " + attrib.getRole();
            }
            if (attrib.hasSingleOpen())
            {
                // System.out.println("\t\tSingleOpen : "+attrib.getSingleOpen());
                temp = temp + "\n\t\tSingleOpen : " + attrib.getSingleOpen();
            }
            if (!temp.isEmpty())
            {
                System.out.println(temp);
            }
            else
            {
                System.out.println("None");
            }
        }
        else
        {
            System.out.println("None");
        }

        // Payload not used
    }

    /**
     * parse msg and print it in a table-nested format to System.out
     */
    public static final void parse(RDMDirectoryResponse response)
    {
        System.out.println(tHeader);
        // MessageModel
        System.out.println(tDomainType + response.getDomainType());

        // MessageType
        System.out.print(tRespMsgType);
        System.out.println(response.getMessageType());
      
        // Response Status
        System.out.print(tStatus);
        if (response.hasResponseStatus())
        {
            System.out.println(response.getResponseStatus());
        }
        else
        {
            System.out.println("None");
        }

        // QOS not used

        // ResponseTypeNum
        System.out.print(tRespType);
        if (response.hasIsRefreshSolicited())
        {
            System.out.println(response.getIsRefreshSolicited() ? "SOLICITED" : "UNSOLICITED");
        }
        else
        {
            System.out.println("None");
        }

        // Indication Flags
        System.out.print(tInd);
        if (!response.getIndicationMask().isEmpty())
        {
            for (RDMDirectoryResponse.IndicationMask indMask : response.getIndicationMask())
            {
                if (indMask == RDMDirectoryResponse.IndicationMask.CLEAR_CACHE)
                    System.out.print("CLEAR_CACHE ");
                if (indMask == RDMDirectoryResponse.IndicationMask.REFRESH_COMPLETE)
                    System.out.print("REFRESH_COMPLETE ");
                if (indMask == RDMDirectoryResponse.IndicationMask.DO_NOT_CACHE)
                    System.out.print("DO_NOT_CACHE ");
            }
            System.out.println();
        }
        else
        {
            System.out.println("None");
        }

        // Header Not used

        // Sequence Num
        System.out.print(tSeqnum);
        if (response.hasSequenceNum())
        {
            System.out.println(response.getSequenceNum());
        }
        else
        {
            System.out.println("None");
        }
        System.out.print(tSecSeqnum);
        if (response.hasSecondarySequenceNum())
        {
            System.out.println(response.getSecondarySequenceNum());
        }
        else
        {
            System.out.println("None");
        }

        // Permission Data not used

        // AttribInfo
        System.out.println(tAttrib);
        if (response.hasAttrib())
        {
            RDMDirectoryResponseAttrib attrib = response.getAttrib();

            // Data Mask
            System.out.print("\t\tDataMask : ");
            if (attrib.hasFilterMask())
            {
                EnumSet<RDMDirectory.FilterMask> filterList = attrib
                        .getFilterMask();
                System.out.print(filterList.size() + " (");
                boolean isFirst = true;
                for (RDMDirectory.FilterMask dataMask : filterList)
                {
                    if (!isFirst)
                        System.out.print(" | ");
                    System.out.print(dataMask.name());
                    isFirst = false;
                }
                System.out.println(" )");
            }
            else
            {
                System.out.println("None");
            }

            // Name not used for Resp
            // NameType not used
            // ServiceName not used for Resp
            // ServiceID not used for Resp
            // ID not used
            // AttribInfo.Attrib not used
        }
        else
        {
            System.out.println("None");
        }

        // Payload
        System.out.print(tPayload);
        if (response.hasPayload())
        {
            RDMDirectoryResponsePayload payload = response.getPayload();
            // / size ?

            if (payload.hasServiceList())
            {
                System.out.println();
                RDMDirectory.ServiceList serviceList = payload.getServiceList();
                if (!serviceList.isEmpty())
                {
                    Iterator<Service> iter = serviceList.iterator();
                    while (iter.hasNext())
                    {
                        Service service = iter.next();
                        // Service Name
                        System.out.print("\t\tService : ");
                        if (service.hasServiceName())
                        {
                            System.out.println(service.getServiceName());
                        }
                        else
                        {
                            System.out.println("None");
                        }
                        // Action
                        System.out.print("\t\t\tAction : ");
                        if (service.hasAction())
                        {
                            System.out.println(service.getAction());
                        }
                        else
                        {
                            System.out.println("None");
                        }
                        // Info
                        if (service.hasInfoFilter())
                        {
                            InfoFilter info = service.getInfoFilter();
                            System.out.print("\t\t\t" + info.getFilterId() + " : ");
                            if (info.hasFilterAction())
                            {
                                System.out.println("Action." + info.getFilterAction());
                            }
                            System.out.print("\t\t\t\tSeviceID : ");
                            if (info.hasServiceId())
                            {
                                System.out.println(info.getServiceId());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tVendor : ");
                            if (info.hasVendor())
                            {
                                System.out.println(info.getVendor());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tIsSource : ");
                            if (info.hasIsSource())
                            {
                                if (info.getIsSource())
                                {
                                    System.out.println("Yes");
                                }
                                else
                                {
                                    System.out.println("No");
                                }
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tCapabilities : ");
                            if (info.hasCapabilityList())
                            {
                                System.out.println();
                                for (RDMDirectory.Capability cap : info.getCapabilityList())
                                {
                                    System.out.println("\t\t\t\t\t" + cap);
                                }
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tDictionariesProvided : ");
                            if (info.hasDictionaryProvidedList())
                            {
                                System.out.println();
                                for (String dictProv : info.getDictionaryProvidedList())
                                {
                                    System.out.println("\t\t\t\t\t" + dictProv);
                                }
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tDictionariesUsed : ");
                            if (info.hasDictionaryUsedList())
                            {
                                System.out.println();
                                for (String dictProv : info.getDictionaryUsedList())
                                {
                                    System.out.println("\t\t\t\t\t" + dictProv);
                                }
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tQoS : ");
                            if (info.hasQosList())
                            {
                                System.out.println();
                                for (QualityOfService qos : info.getQosList())
                                {
                                    System.out.println("\t\t\t\t\t" + qos);
                                }
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tSupportsQoSRange : ");
                            if (info.hasSupportsQoSRange())
                            {
                                System.out.println(info.getSupportsQoSRange());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tItemList : ");
                            if (info.hasItemList())
                            {
                                System.out.println(info.getItemList());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tSupportsOutOfBandSnapshots : ");
                            if (info.hasSupportsOutOfBandSnapshots())
                            {
                                System.out.println(info.getSupportsOutOfBandSnapshots());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tAcceptingConsumerStatus : ");
                            if (info.hasAcceptingConsumerStatus())
                            {
                                System.out.println(info.getAcceptingConsumerStatus());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                        }
                        else
                        {
                            System.out.println("\t\t\tINFO : None");
                        }
                        // End Info
                        // State
                        if (service.hasStateFilter())
                        {
                            com.thomsonreuters.rfa.valueadd.domainrep.rdm.directory.Service.StateFilter state = service
                                    .getStateFilter();
                            System.out.print("\t\t\t" + state.getFilterId() + " : ");
                            if (state.hasFilterAction())
                            {
                                System.out.println("Action." + state.getFilterAction());
                            }
                            System.out.print("\t\t\t\tService State : ");
                            if (state.hasServiceUp())
                            {
                                System.out.println(state.getServiceUp());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tAcceptingRequests : ");
                            if (state.hasAcceptingRequests())
                            {
                                if (state.getAcceptingRequests())
                                {
                                    System.out.println("Yes");
                                }
                                else
                                {
                                    System.out.println("No");
                                }

                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tStatus : ");
                            if (state.hasStatus())
                            {
                                System.out.println(state.getStatus());
                                // System.out.println("xxx");
                            }
                            else
                            {
                                // WRONG
                                System.out.println(state.getStatus());
                                // System.out.println("None");
                            }
                            // End State
                        }
                        else
                        {
                            System.out.println("\t\t\tSTATE : None");
                        }
                        // Group
                        if (service.hasGroupFilterList())
                        {
                            for (GroupFilter group : service.getGroupFilterList())
                            {
                                System.out.print("\t\t\t" + group.getFilterId() + " : ");
                                if (group.hasFilterAction())
                                {
                                    System.out.println("Action." + group.getFilterAction());
                                }
                                System.out.print("\t\t\t\tItemGroup : ");
                                if (group.hasGroup())
                                {
                                    System.out.println(group.getGroup());
                                }
                                else
                                {
                                    System.out.println("None");
                                }
                                System.out.print("\t\t\t\tMergedToGroup : ");
                                if (group.hasMergedToGroup())
                                {
                                    System.out.println(group.getMergedToGroup());
                                }
                                else
                                {
                                    System.out.println("None");
                                }
                                System.out.print("\t\t\t\tStatus : ");
                                if (group.hasStatus())
                                {
                                    System.out.println(group.getStatus());
                                }
                                else
                                {
                                    System.out.println("None");
                                }
                            }
                        }
                        else
                        {
                            System.out.println("\t\t\tGROUP : None");
                        } // End Group
                          // Load
                        if (service.hasLoadFilter())
                        {
                            LoadFilter load = service.getLoadFilter();
                            System.out.print("\t\t\t" + load.getFilterId() + " : ");
                            if (load.hasFilterAction())
                            {
                                System.out.println("Action." + load.getFilterAction());
                            }
                            System.out.print("\t\t\t\tOPenLimit : ");
                            if (load.hasOpenLimit())
                            {
                                System.out.println(load.getOpenLimit());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tOpenWindow : ");
                            if (load.hasOpenWindow())
                            {
                                System.out.println(load.getOpenWindow());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tLoadFactor : ");
                            if (load.hasLoadFactor())
                            {
                                System.out.println(load.getLoadFactor());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                        }
                        else
                        {
                            System.out.println("\t\t\tLOAD : None");
                        } // End Load
                          // Data
                        if (service.hasDataFilter())
                        {
                            DataFilter data = service.getDataFilter();
                            System.out.print("\t\t\t" + data.getFilterId() + " : ");
                            if (data.hasFilterAction())
                            {
                                System.out.println("Action." + data.getFilterAction());
                            }
                            System.out.print("\t\t\t\tType : ");
                            if (data.hasType())
                            {
                                System.out.println(data.getType());
                            }
                            else
                            {
                                System.out.println("None");
                            }
                            System.out.print("\t\t\t\tData : ");
                            // not finish yet *****
                            if (data.hasData())
                            {
                                byte[] dbyte = data.getData();
                                System.out.println(HexDump.toHexString(dbyte, false) + "(length ="
                                        + dbyte.length + ")");
                            }
                            else
                            {
                                System.out.println("None");
                            }
                        }
                        else
                        {
                            System.out.println("\t\t\tDATA : None");
                        }// End data
                         // Link
                        if (service.hasLinkFilter())
                        {
                            LinkFilter link = service.getLinkFilter();
                            System.out.print("\t\t\t" + link.getFilterId() + " : ");
                            if (link.hasFilterAction())
                            {
                                System.out.println("Action." + link.getFilterAction());
                            }
                            
                            if (!link.hasLinkList() && !link.getLinkList().isEmpty())
                            {
                                Iterator<Link> liter = link.getLinkList().iterator();
                                int i = 1;
                                while (liter.hasNext())
                                {
                                    System.out.println("\t\t\t\tLink" + i + " : ");
                                    Link lentry = liter.next();
                                    System.out.print("\t\t\t\t\tType : ");
                                    if (lentry.hasLinkType())
                                    {
                                        System.out.println(lentry.getLinkType());
                                    }
                                    else
                                    {
                                        System.out.println("None");
                                    }
                                    System.out.print("\t\t\t\t\tLinkState : ");
                                    if (lentry.hasLinkState())
                                    {
                                        System.out.println(lentry.getLinkState());
                                    }
                                    System.out.print("\t\t\t\t\tLinkCode : ");
                                    if (lentry.hasLinkCode())
                                    {
                                        System.out.println(lentry.getLinkCode());
                                    }
                                    else
                                    {
                                        System.out.println("None");
                                    }
                                    System.out.print("\t\t\t\t\tText : ");
                                    if (lentry.hasText())
                                    {
                                        System.out.println(lentry.getText());
                                    }
                                    else
                                    {
                                        System.out.println("None");
                                    }

                                }
                            }
                        }
                        else
                        {
                            System.out.println("\t\t\tLINK : None");
                        } // End Link

                        System.out.println();
                    } // End while service
                }
            }
            else
            {
                System.out.println("No Services");
            }
        }

    }

    public static final void parse(RDMDirectoryRequest request)
    {
        System.out.println(tHeader);
        System.out.println(tDomainType + request.getDomainType());

        // Message Type
        System.out.print(tReqMsgType);
        System.out.println(request.getMessageType());
        
        // Indication Flags
        System.out.print(tInd);
        if (!request.getIndicationMask().isEmpty())
        {
            for (RDMDirectoryRequest.IndicationMask indMask : request.getIndicationMask())
            {
                if (indMask == RDMDirectoryRequest.IndicationMask.NONSTREAMING)
                    System.out.print("NONSTREAMING ");
                if (indMask == RDMDirectoryRequest.IndicationMask.ATTRIB_INFO_IN_UPDATES)
                    System.out.print("ATTRIB_INFO_IN_UPDATES ");
                if (indMask == RDMDirectoryRequest.IndicationMask.REFRESH)
                    System.out.print("REFRESH ");
            }
            System.out.println();
        }
        else
        {
            System.out.println("None");
        }
        
        // AttribInfo
        System.out.println(tAttrib);
        if (request.hasAttrib())
        {
            RDMDirectoryRequestAttrib attrib = request.getAttrib();
            System.out.print("\t\tFilter : ");
            if (attrib.hasFilterMask())
            {
                EnumSet<RDMDirectory.FilterMask> filterList = attrib
                        .getFilterMask();
                Iterator<RDMDirectory.FilterMask> iter = filterList
                        .iterator();
                System.out.print(filterList.size() + " (");
                while (iter.hasNext())
                {
                    System.out.print(iter.next().name());
                    if (iter.hasNext())
                        System.out.print(" | ");

                }
                System.out.println(" )");
            }
            else
            {
                System.out.println("None");
            }
        }
        else
        {
            System.out.println("None");
        }
        // Payload
        System.out.println(tPayload + "None");
    }

    public static final void parse(RDMDictionaryRequest request)
    {
        System.out.println(tHeader);
        System.out.println(tDomainType + request.getDomainType());
        
        // Indication Flags
        System.out.print(tInd);
        if (!request.getIndicationMask().isEmpty())
        {
            for (RDMDictionaryRequest.IndicationMask indMask : request.getIndicationMask())
            {
                if (indMask == RDMDictionaryRequest.IndicationMask.NONSTREAMING)
                    System.out.print("NONSTREAMING ");
                if (indMask == RDMDictionaryRequest.IndicationMask.REFRESH)
                    System.out.print("REFRESH ");
            }
            System.out.println();
        }
        else
        {
            System.out.println("None");
        }
    }

    public static final void parse(OMMMsg msg)
    {
        parseMsg(msg, System.out);
    }

    private static final String hintString(OMMMsg msg)
    {
        StringBuilder buf = new StringBuilder(60);

        boolean bAppend = true;

        if (msg.has(OMMMsg.HAS_ATTRIB_INFO))
        {
            bAppend = append(buf, "HAS_ATTRIB_INFO", bAppend);
        }
        if (msg.has(OMMMsg.HAS_CONFLATION_INFO))
        {
            bAppend = append(buf, "HAS_CONFLATION_INFO", bAppend);
        }
        if (msg.has(OMMMsg.HAS_HEADER))
        {
            bAppend = append(buf, "HAS_HEADER", bAppend);
        }
        if (msg.has(OMMMsg.HAS_ITEM_GROUP))
        {
            bAppend = append(buf, "HAS_ITEM_GROUP", bAppend);
        }
        if (msg.has(OMMMsg.HAS_PERMISSION_DATA))
        {
            bAppend = append(buf, "HAS_PERMISSION_DATA", bAppend);
        }
        if (msg.has(OMMMsg.HAS_PRIORITY))
        {
            bAppend = append(buf, "HAS_PRIORITY", bAppend);
        }
        if (msg.has(OMMMsg.HAS_QOS))
        {
            bAppend = append(buf, "HAS_QOS", bAppend);
        }
        if (msg.has(OMMMsg.HAS_QOS_REQ))
        {
            bAppend = append(buf, "HAS_QOS_REQ", bAppend);
        }
        if (msg.has(OMMMsg.HAS_RESP_TYPE_NUM))
        {
            bAppend = append(buf, "HAS_RESP_TYPE_NUM", bAppend);
        }
        if (msg.has(OMMMsg.HAS_SEQ_NUM))
        {
            bAppend = append(buf, "HAS_SEQ_NUM", bAppend);
        }
        if (msg.has(OMMMsg.HAS_ID))
        {
            bAppend = append(buf, "HAS_ID", bAppend);
        }
        if (msg.has(OMMMsg.HAS_PUBLISHER_INFO))
        {
            bAppend = append(buf, "HAS_PUBLISHER_INFO", bAppend);
        }
        if (msg.has(OMMMsg.HAS_STATE))
        {
            bAppend = append(buf, "HAS_STATE", bAppend);
        }

        return buf.toString();
    }

    private static boolean append(StringBuilder buf, String str, boolean first)
    {
        if (!first)
        {
            buf.append(" | ");
            first = false;
        }
        else
            first = false;

        buf.append(str);
        return first;
    }

    /**
     * parse msg and print it in a table-nested format to the provided
     * PrintStream
     */
    public static final void parseMsg(OMMMsg msg, PrintStream ps)
    {
        parseMsg(msg, ps, 0);
    }

    static final void parseMsg(OMMMsg msg, PrintStream ps, int tabLevel)
    {
        msg.getMsgType();
        dumpIndent(ps, tabLevel);
        ps.println("MESSAGE");
        dumpIndent(ps, tabLevel + 1);
        ps.println("Msg Type: " + OMMMsg.MsgType.toString(msg.getMsgType()));
        dumpIndent(ps, tabLevel + 1);
        ps.println("Msg Model Type: " + RDMMsgTypes.toString(msg.getMsgModelType()));
        dumpIndent(ps, tabLevel + 1);
        ps.println("Indication Flags: " + OMMMsg.Indication.indicationString(msg));

        dumpIndent(ps, tabLevel + 1);
        ps.println("Hint Flags: " + hintString(msg));

        if ((msg.getDataType() == OMMTypes.ANSI_PAGE) && msg.isSet(OMMMsg.Indication.CLEAR_CACHE))
        {
            CURRENT_PAGE = null;
        }

        if (msg.has(OMMMsg.HAS_STATE))
        {
            dumpIndent(ps, tabLevel + 1);
            ps.println("State: " + msg.getState());
        }
        if (msg.has(OMMMsg.HAS_PRIORITY))
        {
            dumpIndent(ps, tabLevel + 1);
            OMMPriority p = msg.getPriority();
            if (p != null)
                ps.println("Priority: " + p.getPriorityClass() + "," + p.getCount());
            else
                ps.println("Priority: Error flag recieved but there is not priority present");
        }
        if (msg.has(OMMMsg.HAS_QOS))
        {
            dumpIndent(ps, tabLevel + 1);
            ps.println("Qos: " + msg.getQos());
        }
        if (msg.has(OMMMsg.HAS_QOS_REQ))
        {
            dumpIndent(ps, tabLevel + 1);
            ps.println("QosReq: " + msg.getQosReq());
        }
        if (msg.has(OMMMsg.HAS_ITEM_GROUP))
        {
            dumpIndent(ps, tabLevel + 1);
            ps.println("Group: " + msg.getItemGroup());
        }
        if (msg.has(OMMMsg.HAS_PERMISSION_DATA))
        {
            dumpIndent(ps, tabLevel + 1);
            ps.print("PermissionData: " + HexDump.toHexString(msg.getPermissionData(), false));
        }
        if (msg.has(OMMMsg.HAS_SEQ_NUM))
        {
            dumpIndent(ps, tabLevel + 1);
            ps.println("SeqNum: " + msg.getSeqNum());
        }

        if (msg.has(OMMMsg.HAS_CONFLATION_INFO))
        {
            dumpIndent(ps, tabLevel + 1);
            ps.println("Conflation Count: " + msg.getConflationCount());
            dumpIndent(ps, tabLevel + 1);
            ps.println("Conflation Time: " + msg.getConflationTime());
        }

        if (msg.has(OMMMsg.HAS_RESP_TYPE_NUM))
        {
            dumpIndent(ps, tabLevel + 1);
            ps.print("RespTypeNum: " + msg.getRespTypeNum());
            dumpRespTypeNum(msg, ps);
        }

        if (msg.has(OMMMsg.HAS_ID))
        {
            dumpIndent(ps, tabLevel + 1);
            ps.println("Id: " + msg.getId());
        }

        if ((msg.has(OMMMsg.HAS_PUBLISHER_INFO)) || (msg.getMsgType() == OMMMsg.MsgType.POST))
        {
            PublisherPrincipalIdentity pi = (PublisherPrincipalIdentity)msg.getPrincipalIdentity();
            if (pi != null)
            {
                dumpIndent(ps, tabLevel + 1);
                ps.println("Publisher Address: 0x" + Long.toHexString(pi.getPublisherAddress()));
                dumpIndent(ps, tabLevel + 1);
                ps.println("Publisher Id: " + pi.getPublisherId());
            }
        }

        if (msg.has(OMMMsg.HAS_ATTRIB_INFO))
        {
            dumpIndent(ps, tabLevel + 1);
            ps.println("AttribInfo");
            OMMAttribInfo ai = msg.getAttribInfo();
            if (ai.has(OMMAttribInfo.HAS_SERVICE_NAME))
            {
                dumpIndent(ps, tabLevel + 2);
                ps.println("ServiceName: " + ai.getServiceName());
            }
            if (ai.has(OMMAttribInfo.HAS_SERVICE_ID))
            {
                dumpIndent(ps, tabLevel + 2);
                ps.println("ServiceId: " + ai.getServiceID());
            }
            if (ai.has(OMMAttribInfo.HAS_NAME))
            {
                dumpIndent(ps, tabLevel + 2);
                ps.println("Name: " + ai.getName());
            }
            if (ai.has(OMMAttribInfo.HAS_NAME_TYPE))
            {
                dumpIndent(ps, tabLevel + 2);
                ps.print("NameType: " + ai.getNameType());
                if (msg.getMsgModelType() == RDMMsgTypes.LOGIN)
                {
                    ps.println(" (" + RDMUser.NameType.toString(ai.getNameType()) + ")");
                }
                else if (RDMInstrument.isInstrumentMsgModelType(msg.getMsgModelType()))
                {

                    ps.println(" (" + RDMInstrument.NameType.toString(ai.getNameType()) + ")");
                }
                else
                {
                    ps.println();
                }
            }
            if (ai.has(OMMAttribInfo.HAS_FILTER))
            {
                dumpIndent(ps, tabLevel + 2);
                ps.print("Filter: " + ai.getFilter());
                if (msg.getMsgModelType() == RDMMsgTypes.DIRECTORY)
                {
                    ps.println(" (" + RDMService.Filter.toString(ai.getFilter()) + ")");
                }
                else if (msg.getMsgModelType() == RDMMsgTypes.DICTIONARY)
                {
                    ps.println(" (" + RDMDictionary.Filter.toString(ai.getFilter()) + ")");
                }
                else
                {
                    ps.println();
                }
            }
            if (ai.has(OMMAttribInfo.HAS_ID))
            {
                dumpIndent(ps, tabLevel + 2);
                ps.println("ID: " + ai.getId());
            }
            if (ai.has(OMMAttribInfo.HAS_ATTRIB))
            {
                dumpIndent(ps, tabLevel + 2);
                ps.println("Attrib");
                parseData(ai.getAttrib(), ps, tabLevel + 2);
            }
        }

        dumpIndent(ps, tabLevel + 1);
        ps.print("Payload: ");
        if (msg.getDataType() != OMMTypes.NO_DATA)
        {
            ps.println(msg.getPayload().getEncodedLength() + " bytes");
            parseData(msg.getPayload(), ps, tabLevel + 1);
        }
        else
        {
            ps.println("None");
        }
    }

    /**
     * parse msg and print it in a table-nested format to the provided
     * PrintStream
     */
    public static final void parseDataDefinition(OMMDataDefs datadefs, short dbtype,
            PrintStream ps, int tabLevel)
    {
        DataDefDictionary listDefDb = DataDefDictionary.create(dbtype);
        DataDefDictionary.decodeOMMDataDefs(listDefDb, datadefs);

        ps.print("DATA_DEFINITIONS ");
        for (Iterator<?> listDefDbIter = listDefDb.iterator(); listDefDbIter.hasNext();)
        {
            DataDef listdef = (DataDef)listDefDbIter.next();

            ps.print("Count: ");
            ps.print(listdef.getCount());
            ps.print(" DefId: ");
            ps.println(listdef.getDataDefId());

            if (dbtype == OMMTypes.ELEMENT_LIST_DEF_DB)
            {
                for (Iterator<?> listdefIter = listdef.iterator(); listdefIter.hasNext();)
                {
                    ElementEntryDef ommEntry = (ElementEntryDef)listdefIter.next();
                    dumpIndent(ps, tabLevel + 1);
                    ps.print("ELEMENT_ENTRY_DEF ");
                    ps.print("Name: ");
                    ps.print(ommEntry.getName());
                    ps.print(" Type: ");
                    ps.println(OMMTypes.toString(ommEntry.getDataType()));
                }
            }
            else
            {
                for (Iterator<?> listdefIter = listdef.iterator(); listdefIter.hasNext();)
                {
                    FieldEntryDef ommEntry = (FieldEntryDef)listdefIter.next();
                    dumpIndent(ps, tabLevel + 1);
                    ps.print("FIELD_ENTRY_DEF ");
                    ps.print("FID: ");
                    ps.print(ommEntry.getFieldId());
                    ps.print(" Type: ");
                    ps.println(OMMTypes.toString(ommEntry.getDataType()));
                }
            }
        }
    }

    private static void dumpRespTypeNum(OMMMsg msg, PrintStream ps)
    {
        if (msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
        {
            ps.println(" (" + OMMMsg.RespType.toString(msg.getRespTypeNum()) + ")");
        }
        else
        // msg.getMsgType() == OMMMsg.OMMMsg.MsgType.UPDATE_RESP
        {
            if ((msg.getMsgModelType() >= RDMMsgTypes.MARKET_PRICE)
                    && (msg.getMsgModelType() <= RDMMsgTypes.HISTORY))
            {
                ps.println(" (" + RDMInstrument.Update.toString(msg.getRespTypeNum()) + ")");
            }
        }
    }

    /**
     * parse data and print it in a table-nested format to the System.out
     */

    public static final void parse(OMMData data)
    {
        parseData(data, System.out, 0);
    }

    private static final void parseAggregate(OMMData data, PrintStream ps, int tabLevel)
    {
        parseAggregateHeader(data, ps, tabLevel);
        for (Iterator<?> iter = ((OMMIterable)data).iterator(); iter.hasNext();)
        {
            OMMEntry entry = (OMMEntry)iter.next();
            parseEntry(entry, ps, tabLevel + 1);
        }
    }

    /**
     * parse data and print it in a table-nested format to the provided
     * PrintStream
     */
    public static final void parseData(OMMData data, PrintStream ps, int tabLevel)
    {
        if (data.isBlank())
            dumpBlank(ps);
        else if (OMMTypes.isAggregate(data.getType()))
            parseAggregate(data, ps, tabLevel + 1);
        else if ((data.getType() == OMMTypes.RMTES_STRING)
                && ((OMMDataBuffer)data).hasPartialUpdates())
        {
            Iterator<?> iter = ((OMMDataBuffer)data).partialUpdateIterator();
            while (true)
            {
                OMMDataBuffer partial = (OMMDataBuffer)iter.next();
                ps.print("hpos: ");
                ps.print(partial.horizontalPosition());
                ps.print(", ");
                ps.print(partial.toString());
                if (iter.hasNext())
                    ps.print("  |  ");
                else
                    break;
            }
            ps.println();
        }
        else if (data.getType() == OMMTypes.ANSI_PAGE)
        {
            // process ANSI with com.reuters.rfa.ansipage
            parseAnsiPageData(data, ps, tabLevel);
        }
        else if (data.getType() == OMMTypes.BUFFER || data.getType() == OMMTypes.OPAQUE_BUFFER)
        {
            if (data.getEncodedLength() <= 20)
            {
                dumpIndent(ps, tabLevel + 1);
                // for small strings, print hex and try to print ASCII
                ps.print(HexDump.toHexString(((OMMDataBuffer)data).getBytes(), false));
                ps.print(" | ");
                ps.println(data);
            }
            else
            {
                if (INTERNAL_DEBUG)
                {
                    ps.println("Hex Format and Data Bytes: ");
                    ps.println(HexDump.hexDump(((OMMDataBuffer)data).getBytes(), 50));

                    ps.println("Hex Format: ");
                }

                int lineSize = 32;
                String s = HexDump.toHexString(((OMMDataBuffer)data).getBytes(), false);

                int j = 0;
                while (j < s.length())
                {
                    if (j != 0)
                        ps.println();

                    dumpIndent(ps, 1);

                    int end = j + lineSize;
                    if (end >= s.length())
                        end = s.length();

                    for (int i = j; i < end; i++)
                    {
                        ps.print(s.charAt(i));
                    }
                    j = j + lineSize;
                }

                ps.println("\nData Bytes: ");
                dumpIndent(ps, 1);
                ps.println(data);
            }
        }
        else if (data.getType() == OMMTypes.MSG)
        {
            parseMsg((OMMMsg)data, ps, tabLevel + 1);
        }
        else
        {
            try
            {
                ps.println(data);
            }
            catch (Exception e)
            {
                byte[] rawdata = data.getBytes();
                ps.println(HexDump.hexDump(rawdata));
            }
        }
    }

    private static final void parseAggregateHeader(OMMData data, PrintStream ps, int tabLevel)
    {
        dumpIndent(ps, tabLevel);
        short dataType = data.getType();
        ps.println(OMMTypes.toString(dataType));
        switch (dataType)
        {
            case OMMTypes.FIELD_LIST:
            {
                // set DICTIONARY to the dictId for this field list
                OMMFieldList fieldList = (OMMFieldList)data;
                int dictId = fieldList.getDictId();
                CURRENT_DICTIONARY = getDictionary(dictId);
            }
                break;
            case OMMTypes.SERIES:
            {
                OMMSeries s = (OMMSeries)data;
                if (s.has(OMMSeries.HAS_SUMMARY_DATA))
                {
                    dumpIndent(ps, tabLevel + 1);
                    ps.println("SUMMARY");
                    parseData(s.getSummaryData(), ps, tabLevel + 1);
                }
                if (s.has(OMMSeries.HAS_DATA_DEFINITIONS))
                {
                    dumpIndent(ps, tabLevel + 1);
                    short dbtype = s.getDataType() == OMMTypes.FIELD_LIST ? OMMTypes.FIELD_LIST_DEF_DB
                            : OMMTypes.ELEMENT_LIST_DEF_DB;
                    parseDataDefinition(s.getDataDefs(), dbtype, ps, tabLevel + 1);
                }
            }
                break;
            case OMMTypes.MAP:
            {
                OMMMap s = (OMMMap)data;

                String flagsString = mapFlagsString(s);
                dumpIndent(ps, tabLevel);
                ps.print("flags: ");
                ps.println(flagsString);

                if (s.has(OMMMap.HAS_SUMMARY_DATA))
                {
                    dumpIndent(ps, tabLevel + 1);
                    ps.println("SUMMARY");
                    parseData(s.getSummaryData(), ps, tabLevel + 1);
                }
            }
                break;
            case OMMTypes.VECTOR:
            {
                OMMVector s = (OMMVector)data;

                String flagsString = vectorFlagsString(s);
                dumpIndent(ps, tabLevel);
                ps.print("flags: ");
                ps.println(flagsString);

                if (s.has(OMMVector.HAS_SUMMARY_DATA))
                {
                    dumpIndent(ps, tabLevel + 1);
                    ps.println("SUMMARY");
                    parseData(s.getSummaryData(), ps, tabLevel + 1);
                }
            }
                break;
            case OMMTypes.FILTER_LIST:
            {
                OMMFilterList s = (OMMFilterList)data;

                String flagsString = filterListFlagsString(s);
                dumpIndent(ps, tabLevel);
                ps.print("flags: ");
                ps.println(flagsString);
            }
                break;
        }
    }

    private static final void dumpBlank(PrintStream ps)
    {
        ps.println();
    }

    private static final void dumpIndent(PrintStream ps, int tabLevel)
    {
        for (int i = 0; i < tabLevel; i++)
            ps.print('\t');
    }

    private static final void parseEntry(OMMEntry entry, PrintStream ps, int tabLevel)
    {
        try
        {
            switch (entry.getType())

            {
                case OMMTypes.FIELD_ENTRY:
                {
                    OMMFieldEntry fe = (OMMFieldEntry)entry;
                    if (CURRENT_DICTIONARY != null)
                    {
                        FidDef fiddef = CURRENT_DICTIONARY.getFidDef(fe.getFieldId());
                        if (fiddef != null)
                        {
                            dumpFieldEntryHeader(fe, fiddef, ps, tabLevel);
                            OMMData data = null;
                            if (fe.getDataType() == OMMTypes.UNKNOWN)
                                data = fe.getData(fiddef.getOMMType());
                            else
                                // defined data already has type
                                data = fe.getData();
                            if (data.getType() == OMMTypes.ENUM)
                            {
                                ps.print(CURRENT_DICTIONARY.expandedValueFor(fiddef.getFieldId(),
                                                                             ((OMMEnum)data)
                                                                                     .getValue()));
                                ps.print(" (");
                                ps.print(data);
                                ps.println(")");
                            }
                            else
                                parseData(data, ps, tabLevel);
                        }
                        else
                        {
                            ps.println("Received field id: " + fe.getFieldId()
                                    + " - Not defined in dictionary");
                        }
                    }
                    else
                    {
                        dumpFieldEntryHeader(fe, null, ps, tabLevel);
                        if (fe.getDataType() == OMMTypes.UNKNOWN)
                        {
                            OMMDataBuffer data = (OMMDataBuffer)fe.getData();
                            ps.println(HexDump.toHexString(data.getBytes(), false));
                        }
                        else
                        // defined data already has type
                        {
                            OMMData data = fe.getData();
                            parseData(data, ps, tabLevel);
                        }
                    }
                    ps.flush();
                }
                    break;
                case OMMTypes.ELEMENT_ENTRY:
                    dumpElementEntryHeader((OMMElementEntry)entry, ps, tabLevel);
                    parseData(entry.getData(), ps, tabLevel);
                    break;
                case OMMTypes.MAP_ENTRY:
                    dumpMapEntryHeader((OMMMapEntry)entry, ps, tabLevel);
                    if ((((OMMMapEntry)entry).getAction() != OMMMapEntry.Action.DELETE)
                            && entry.getDataType() != OMMTypes.NO_DATA)
                        parseData(entry.getData(), ps, tabLevel);
                    break;
                case OMMTypes.VECTOR_ENTRY:
                    dumpVectorEntryHeader((OMMVectorEntry)entry, ps, tabLevel);
                    if ((((OMMVectorEntry)entry).getAction() != OMMVectorEntry.Action.DELETE)
                            && (((OMMVectorEntry)entry).getAction() != OMMVectorEntry.Action.CLEAR))
                        parseData(entry.getData(), ps, tabLevel);
                    break;
                case OMMTypes.FILTER_ENTRY:
                    dumpFilterEntryHeader((OMMFilterEntry)entry, ps, tabLevel);
                    if (((OMMFilterEntry)entry).getAction() != OMMFilterEntry.Action.CLEAR)
                        parseData(entry.getData(), ps, tabLevel);
                    break;
                default:
                    dumpEntryHeader(entry, ps, tabLevel);
                    parseData(entry.getData(), ps, tabLevel);
                    break;
            }
        }
        catch (OMMException e)
        {
            ps.println("ERROR Invalid data: " + e.getMessage());
        }
    }

    private static final void dumpEntryHeader(OMMEntry entry, PrintStream ps, int tabLevel)
    {
        dumpIndent(ps, tabLevel);
        ps.print(OMMTypes.toString(entry.getType()));
        ps.print(": ");
        if (entry.getType() == OMMTypes.SERIES_ENTRY)
            ps.println();
        // else array entry value is on same line
    }

    private static final void dumpFieldEntryHeader(OMMFieldEntry entry, FidDef def, PrintStream ps,
            int tabLevel)
    {
        dumpIndent(ps, tabLevel);
        ps.print(OMMTypes.toString(entry.getType()));
        ps.print(" ");
        ps.print(entry.getFieldId());
        if (def == null)
        {
            ps.print(": ");
        }
        else
        {
            ps.print("/");
            ps.print(def.getName());
            ps.print(": ");
            if ((def.getOMMType() >= OMMTypes.BASE_FORMAT) || (def.getOMMType() == OMMTypes.ARRAY))
                ps.println();
        }
    }

    private static final void dumpElementEntryHeader(OMMElementEntry entry, PrintStream ps,
            int tabLevel)
    {
        dumpIndent(ps, tabLevel);
        ps.print(OMMTypes.toString(entry.getType()));
        ps.print(" ");
        ps.print(entry.getName());
        ps.print(": ");
        if ((entry.getDataType() >= OMMTypes.BASE_FORMAT)
                || (entry.getDataType() == OMMTypes.ARRAY))
            ps.println();

    }

    private static final void dumpFilterEntryHeader(OMMFilterEntry entry, PrintStream ps,
            int tabLevel)
    {
        dumpIndent(ps, tabLevel);
        ps.print(OMMTypes.toString(entry.getType()));
        ps.print(" ");
        ps.print(entry.getFilterId());
        ps.print(" (");
        ps.print(OMMFilterEntry.Action.toString(entry.getAction()));
        if (entry.has(OMMFilterEntry.HAS_PERMISSION_DATA))
            ps.print(", HasPermissionData");
        if (entry.has(OMMFilterEntry.HAS_DATA_FORMAT))
            ps.print(", HasDataFormat");
        ps.println(") : ");

        String flagsString = filterEntryFlagsString(entry);
        dumpIndent(ps, tabLevel);
        ps.print("flags: ");
        ps.println(flagsString);

    }

    private static final void dumpMapEntryHeader(OMMMapEntry entry, PrintStream ps, int tabLevel)
    {
        dumpIndent(ps, tabLevel);
        ps.print(OMMTypes.toString(entry.getType()));
        ps.print(" (");
        ps.print(OMMMapEntry.Action.toString(entry.getAction()));
        if (entry.has(OMMMapEntry.HAS_PERMISSION_DATA))
            ps.print(", HasPermissionData");
        ps.println(") : ");

        String flagsString = mapEntryFlagsString(entry);
        dumpIndent(ps, tabLevel);
        ps.print("flags: ");
        ps.println(flagsString);

        dumpIndent(ps, tabLevel);
        ps.print("Key: ");
        parseData(entry.getKey(), ps, 0);
        dumpIndent(ps, tabLevel);
        ps.println("Value: ");
    }

    private static final void dumpVectorEntryHeader(OMMVectorEntry entry, PrintStream ps,
            int tabLevel)
    {
        dumpIndent(ps, tabLevel);
        ps.print(OMMTypes.toString(entry.getType()));
        ps.print(" ");
        ps.print(entry.getPosition());
        ps.print(" (");
        ps.print(OMMVectorEntry.Action.vectorActionString(entry.getAction()));
        if (entry.has(OMMVectorEntry.HAS_PERMISSION_DATA))
            ps.print(", HasPermissionData");
        ps.println(") : ");

        String flagsString = vectorEntryFlagsString(entry);
        dumpIndent(ps, tabLevel);
        ps.print("flags: ");
        ps.println(flagsString);

    }

    public static final void parseAnsiPageData(OMMData data, PrintStream ps, int tabLevel)
    {
        boolean newPage = false;
        if (CURRENT_PAGE == null)
        {
            CURRENT_PAGE = new Page();
            newPage = true;
        }

        Vector<PageUpdate> pageUpdates = new Vector<PageUpdate>();
        ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes());
        CURRENT_PAGE.decode(bais, pageUpdates);
        if (newPage)
            ps.println(CURRENT_PAGE.toString()); // print the page if it is a
                                                 // refresh message
        else
        {
            // print the update string
            Iterator<PageUpdate> iter = pageUpdates.iterator();
            while (iter.hasNext())
            {
                PageUpdate u = (PageUpdate)iter.next();
                StringBuilder buf = new StringBuilder(80);
                for (short k = u.getBeginningColumn(); k < u.getEndingColumn(); k++)
                {
                    buf.append(CURRENT_PAGE.getChar(u.getRow(), k));
                }
                if (!(buf.toString()).equalsIgnoreCase(""))
                {
                    dumpIndent(ps, tabLevel);
                    ps.println("Update String: " + buf.toString() + " (Row: " + u.getRow()
                            + ", Begin Col: " + u.getBeginningColumn() + ", End Col: "
                            + u.getEndingColumn() + ")");
                }
            }
        }
    }

    public static String mapFlagsString(OMMMap data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMMap.HAS_DATA_DEFINITIONS))
        {
            buf.append("HAS_DATA_DEFINITIONS");
        }

        if (data.has(OMMMap.HAS_SUMMARY_DATA))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_SUMMARY_DATA");
        }

        if (data.has(OMMMap.HAS_PERMISSION_DATA_PER_ENTRY))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_PERMISSION_DATA_PER_ENTRY");
        }

        if (data.has(OMMMap.HAS_TOTAL_COUNT_HINT))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_TOTAL_COUNT_HINT");
        }

        if (data.has(OMMMap.HAS_KEY_FIELD_ID))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_KEY_FIELD_ID");
        }
        return buf.toString();
    }

    public static String mapEntryFlagsString(OMMMapEntry data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMMapEntry.HAS_PERMISSION_DATA))
        {
            buf.append("HAS_PERMISSION_DATA");
        }
        return buf.toString();
    }

    public static String vectorFlagsString(OMMVector data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMVector.HAS_DATA_DEFINITIONS))
        {
            buf.append("HAS_DATA_DEFINITIONS");
        }

        if (data.has(OMMVector.HAS_SUMMARY_DATA))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_SUMMARY_DATA");
        }

        if (data.has(OMMVector.HAS_PERMISSION_DATA_PER_ENTRY))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_PERMISSION_DATA_PER_ENTRY");
        }

        if (data.has(OMMVector.HAS_TOTAL_COUNT_HINT))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_TOTAL_COUNT_HINT");
        }

        if (data.has(OMMVector.HAS_SORT_ACTIONS))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_SORT_ACTIONS");
        }
        return buf.toString();
    }

    public static String filterListFlagsString(OMMFilterList data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMFilterList.HAS_PERMISSION_DATA_PER_ENTRY))
        {
            buf.append("HAS_PERMISSION_DATA_PER_ENTRY");
        }

        if (data.has(OMMFilterList.HAS_TOTAL_COUNT_HINT))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_TOTAL_COUNT_HINT");
        }
        return buf.toString();
    }

    public static String vectorEntryFlagsString(OMMVectorEntry data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMVectorEntry.HAS_PERMISSION_DATA))
        {
            buf.append("HAS_PERMISSION_DATA");
        }
        return buf.toString();
    }

    public static String filterEntryFlagsString(OMMFilterEntry data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMFilterEntry.HAS_PERMISSION_DATA))
        {
            buf.append("HAS_PERMISSION_DATA");
        }
        if (data.has(OMMFilterEntry.HAS_DATA_FORMAT))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_DATA_FORMAT");
        }
        return buf.toString();
    }
}
