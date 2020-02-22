pluginRegister.setPluginComponent(new CustomPluginSite());
function CustomPluginSite() {       
    function load(accountName, siteId) {
        var $fileExpression = $('#onedriveDownloadExpression');
        if (siteId) {
        	var selectedCertificateURL = null;
            $.ajax({
                url : "/api/v1.4/sites/" + siteId,
                type: 'GET',
                dataType : 'json',
                cache: false,
                success : function(data) {
                	//alert(data['protocol'] );
                    if (data && data['protocol'] === 'MicrosoftOneDrive') {
                        $.map( data, function( value, key ) {                            
                            var checkedValue = value == 'true' ? true : false;
                            if (key == 'onedriveDownloadExpression') {                          
                                changeChecked($fileExpression, checkedValue);
                                if(checkedValue) {
                                    $('#onedrive_downloadFolder').addClass("template-input");
                                } else {
                                   $('#onedrive_downloadFolder').removeClass("template-input"); 
                                }
                            } else if (key == 'metadata') {
                                if ('links' in value) {
                                    $.map( value['links'], function( linkUrl, link ) {
                                        if (link.match('^clientLocalCertificate')) {
                                            selectedCertificateURL = linkUrl;
                                        }
                                    });
                                }
                            }  else if (key == 'onedrive_networkZone') {
                                custom_fetchDmzZones('onedrive_networkZone', value); // load zones with selected one
                                $('#' + key).val(value);
                            }  else if (key == "onedriveDownloadType") {
                                if (value == "regex") {
                                    $('#onedriveDownloadTypeRegex').prop('checked', true);
                                } else {
                                    $('#onedriveDownloadTypeGlob').prop('checked', true);
                                }
							}else{
                                $('#' + key).val(value);
                            }
                        });  
                    } else {
                        fetchDefaultValues(accountName);
                    }
                }
            });
        } else {
             fetchDefaultValues(accountName);
        }
    } 

    function fetchDefaultValues(accountName) {
        custom_fetchDmzZones('onedrive_networkZone', 'none'); // load zones with default selected
        $('#onedriveDownloadTypeGlob').prop('checked', true);
        $('#onedriveDownloadTypeRegex').prop('checked', true);
    }
    
    function setCheckBox(elementId) {
        var targetElement = $('#' + elementId);
        if (targetElement.prop('checked')) {
            targetElement.val("true");
        } else {
            targetElement.val("false");            
        }
    }

    function save(siteData, callback) {
        setCheckBox("onedriveDownloadExpression");
        if (siteData) {
            var customProperties = {
            	"onedrive_appId": $('#onedrive_appId').val(),
                "onedrive_secret": $('#onedrive_secret').val(),
                "onedrive_tenantId": $('#onedrive_tenantId').val(),
                "onedrive_networkZone": $('#onedrive_networkZone').val(),
                "onedrive_downloadFolder": $('#onedrive_downloadFolder').val(),
                "onedriveDownloadExpression": $('#onedriveDownloadExpression').val(),
                "onedriveDownloadFilePattern": $('#onedriveDownloadFilePattern').val(),
                "onedriveDownloadType": $('input:radio[name=onedriveDownloadType]:checked').val(),
                "onedrive_userEmail": $('#onedrive_userEmail').val()
            },
            siteUrl = "/api/v1.4/sites",
            siteId = siteData.id;
            data = null;
            var validationErr = [];
            $.extend( siteData, customProperties );
            
            // check if there are validation errors
            if (validationErr && validationErr.length > 0) {
                var message = '';
                for(var i = 0; i < validationErr.length; i++) {
                    message += "\n" + validationErr[i];
                }
                if (callback) {
                    callback(message);
                    return;
                }
            }
            
            if (siteId) {
                // update site
                siteUrl += '/' + siteId;
                data = siteData;
            } else {
                data = {'sites': [siteData]};
            }
            $.ajax({
                    url : siteUrl,
                    type: 'POST',
                    dataType : 'json',
                    contentType : 'application/json',
                    data: JSON.stringify(data),
                    success : function() {
                        if (callback) {
                            callback();
                        }
                    }, 
                    error: function(jqXHR, textStatus, errorThrown) {
                        // show 'validationErrors' or 'message' key values
                        var errorMsg = '',
                        jsonDoc = jQuery.parseJSON(jqXHR.responseText),
                        validationErrors = jsonDoc["validationErrors"],
                        message = jsonDoc["message"];

                        if (validationErrors && validationErrors.length > 0) {
                            for(var i = 0; i < validationErrors.length; i++) {
                                errorMsg += "\n" + validationErrors[i];
                            }
                        } else if (message) {
                            errorMsg = message;
                        }

                        if (callback) {
                            callback(errorMsg);
                        }
                    }
            });
        }
    }
    var instance = new PluginComponentInterface();
    instance.load = load;
    instance.save = save;
    return Object.seal(instance);
}
    function changeChecked(id, isChecked){
        id.val(isChecked); 
        if(isChecked){
            id.attr('checked', 'checked');
        }else{
            id.removeAttr('checked');
        }
    }
    function showHideElements(chObjectId, elementId ) {
        var checked = $('#' + chObjectId).is(":checked")
        if (checked) {
            $("#" + elementId).css("display", dhtmlTableRowParam);;
        } else {
            $("#" + elementId).css("display", "none");
        }
        
    }
    $("#onedriveDownloadExpression").change(function() {
        if (this.checked) {
            $('#onedrive_downloadFolder').addClass("template-input");
        } else {
           $('#onedrive_downloadFolder').removeClass("template-input"); 
        }
    });

    /**
     * [BEGIN]  dmzZone functions
     */
    /* Retrieves all DMZ zones from database */
    function custom_fetchDmzZones(selectId, custom_selectedZone) {
        custom_appendDefaultOptions(selectId, custom_selectedZone);
        
        $.ajax({
            url : "/api/v1.0/zones",
            type: 'GET',
            dataType : 'json',
            cache: false,
            success : function(data) {
                var zonesList = data.zones;
                if (zonesList && zonesList.length > 0) {
                    custom_fillDmzZonesSelect(selectId, custom_selectedZone, zonesList);
               }
            },
            error: function(jqXHR, textStatus, errorThrown) {
                alert("Error occurred on retrieving Network Zone.");
            }
        })
    }

    /* fills specified <select> with list of zones and select specified zone */
    function custom_fillDmzZonesSelect(selectId, selectedZone, zonesList) {
       for (var i = 0; i < zonesList.length; i++) {
           var zone = zonesList[i].zone;
           // if not the default zone, add it into the list.
           if ('Private' != zone.name) {
               custom_appendOption(selectId, selectedZone, zone.name);
           }
       }
    }

    function custom_appendDefaultOptions(selectId, selectedZone) {
        custom_appendOption(selectId, selectedZone, 'none');
        custom_appendOption(selectId, selectedZone, 'any');
        custom_appendOption(selectId, selectedZone, 'Default');
    }

    /* append <option> to specified <select> */
    function custom_appendOption(selectId, selectedElement, value) {
       if (selectedElement == value) {
           $('<option value="' + custom_htmlescape(value) + '" selected="selected">' + value + '</option>').appendTo($('#' + selectId));
       } else {
           $('<option value="' + custom_htmlescape(value) + '">' + value + '</option>').appendTo($('#' + selectId));
       }
    }

    /* Escape script characters as <&"\> */
    function custom_htmlescape(str) {
        var ret = str;
        if (str) {
            var regexp = /&/g;
            ret = ret.replace(regexp, "&amp;");
            regexp = /</g;
            ret = ret.replace(regexp, "&lt;");  
            regexp = />/g;  
            ret = ret.replace(regexp, "&gt;");
            regexp = /\"/g; 
            ret = ret.replace(regexp, "&quot;"); 
            regexp = /\\/g; 
            ret = ret.replace(regexp, "&#092;"); 
        }
        return ret;
    }
    /**
     * [END]  dmzZone functions
     */ 