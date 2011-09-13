
function ProcessessFilter() {
	this.filter = [];
}


ProcessessFilter.prototype.apply=function(ordering, offset, size, type) {
	this.filter = [];
	$(".filter-vals").each(bind(function(i,val) {
		if ($(val).val()) {
			if ($(val).hasClass("eq")) {
				this.filter.push({name:$(val).attr('name'),op:"EQ",val:$(val).val()});
			} else if ($(val).hasClass("lt")) {
				this.filter.push({name:$(val).attr('name'),op:"LT",val:$(val).val()});
			} else if ($(val).hasClass("gt")) {
				this.filter.push({name:$(val).attr('name'),op:"GT",val:$(val).val()});
			} else if ($(val).hasClass("like")) {
				var rval = $(val).val();
				if (!rval.startsWith("%")) {
					rval = "%"+rval; 
				}
				if (!rval.endsWith("%")) {
					rval = rval+"%"; 
				}
				rval = escape(rval);
				this.filter.push({name:$(val).attr('name'),op:"LIKE",val:rval});
			}
		}
	},this));
	

	var url = "inc/admin/_processes_data.jsp?ordering="+ordering+"&offset="+offset+"&size="+size+"&type="+type+this.filterPostfix();
	$.get(url, function(data) {
		$("#processes").html(data);
	});
	
	$(".filter").toggle();
    $(".displayButton").toggle();
}


ProcessessFilter.prototype.filterPostfix = function() {
	if (this.filter) {
		var furl = this.curl();
		return "&filter="+this.curl();
	} else return "";
}

ProcessessFilter.prototype.curl=function() {
	return "{"+reduce(function(base, item, status) {
    	base = base+reduceItem(item)+ (status.last ? "": ";");
        return base;
    }, "",this.filter)+"}";    
	
	function reduceItem(item) {
		return "{"+item.name+";"+item.op+";"+item.val.replaceAll(":","\\:")+"}";
	}
}


function Processes() {
	this.dialog = null;
	this.displayedRows = [];
	
	this.currentFilter = new ProcessessFilter();
}

Processes.prototype.openProcessDialog = function() {
	if (this.dialog) {
		this.dialog.dialog('open');
	} else {
		this.dialog = $("#processes").dialog({
	        bgiframe: true,
	        width:  $(window).width()-20,
	        height:  $(window).height()-60,
	        modal: true,
	        title: dictionary['administrator.menu.dialogs.lrprocesses.title'],
	        buttons: {
	            "Close": function() {
	                $(this).dialog("close"); 
	            } 
	        }
	    });
	}
}


Processes.prototype.processes = function (){
	var url = "inc/admin/_processes_data.jsp?offset=0&size=20&type=DESC";
	$.get(url, bind(function(data) {
		this.openProcessDialog();
		this.dialog.dialog('option', 'position', [10, 10]);
		this.dialog.dialog("option", "width", $(window).width()-20);
		this.dialog.dialog("option", "height", $(window).height()-60);
		$("#processes>table").css('height',$(window).height()-160)
		$("#processes").html(data);;
	}, this));
}


Processes.prototype.modifyProcessDialogData = function(ordering, offset, size, type) {
	var url = "inc/admin/_processes_data.jsp?ordering="+ordering+"&offset="+offset+"&size="+size+"&type="+type+this.currentFilter.filterPostfix();
	$.get(url, function(data) {
		$("#processes").html(data);
	});
}

Processes.prototype.doActionAndRefresh=function(url,ordering, offset, size, type) {
	$.get(url, bind(function(fdata) {
		this.refreshProcesses(ordering, offset, size, type);
	},this));
}

Processes.prototype.refreshProcesses = function(ordering, offset, size, type) {
	var refreshurl = "inc/admin/_processes_data.jsp?ordering="+ordering+"&offset="+offset+"&size="+size+"&type="+type;
	$.get(refreshurl, function(sdata) {
		$("#processes").html(sdata);
	});
}

Processes.prototype.subprocesses = function(id) {
    if (this.displayedRows.indexOf(id) >= 0) {
        $("."+id).hide();
        $("#"+id+"_icon").attr("src","img/nolines_plus.gif");
        this.displayedRows.rm(this.displayedRows.indexOf(id));
    } else {
        $("."+id).show();
        $("#"+id+"_icon").attr("src","img/nolines_minus.gif");
        this.displayedRows.push(id);
    }
}

var processes = new Processes();