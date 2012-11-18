$(document).ready(function(){



  $('#msg').focus(function() {
    //console.log("focus on msg : "+targetUser+":"+room);
    $.ajax({
      url: jzChatUpdateUnreadMessages,
      data: {"room": room,
              "user": username,
              "sessionId": sessionId
            },

      success:function(response){
        //console.log("success");
      },

      error:function (xhr, status, error){

      }

    });

  });


  $('#msg').keypress(function(event) {
    var msg = $(this).attr("value");
    if ( event.which == 13 ) {
      //console.log("sendMsg=>"+username + " : " + room + " : "+msg);
      if(!msg)
      {
        return;
      }

      $.ajax({
        url: jzChatSend,
        data: {"user": username,
               "targetUser": targetUser,
               "room": room,
               "message": msg,
               "sessionId": sessionId
              },

        success:function(response){
          //console.log("success");
          document.getElementById("msg").value = '';
          refreshChat();
        },

        error:function (xhr, status, error){

        }

      });
    }

  });

  $(".filter").on("click", function() {
    var child = $("span:first",this);
    if (child.hasClass("filter-on")) {
      child.removeClass("filter-on").addClass("filter-off");
      if ($(this).hasClass("filter-user")) {
        $(".filter-space span:first-child").removeClass("filter-off").addClass("filter-on");
      } else {
        $(".filter-user span:first-child").removeClass("filter-off").addClass("filter-on");
      }
    } else {
      child.removeClass("filter-off").addClass("filter-on");
    }
    refreshWhoIsOnline();
  });

  $('#chatSearch').keyup(function(event) {
    var filter = $(this).attr("value");
    console.log(filter);
    userFilter = filter;
    refreshWhoIsOnline();
  });

  setInterval(refreshWhoIsOnline, 3000);
  refreshWhoIsOnline();

  function refreshWhoIsOnline() {
    var withSpaces = $(".filter-space span:first-child").hasClass("filter-on");
    var withUsers = $(".filter-user span:first-child").hasClass("filter-on");
    $('#whoisonline').load(jzChatWhoIsOnline, {"user": username, "sessionId": sessionId,
      "filter": userFilter, "withSpaces": withSpaces, "withUsers": withUsers}, function () { });
  }

  function strip(html)
  {
    var tmp = document.createElement("DIV");
    tmp.innerHTML = html;
    return tmp.textContent||tmp.innerText;
  }

});