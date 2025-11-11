/**
 * 
 */

window.onload = () => {
  		var alert = document.getElementById('notificationsound');
		
  		document.body.addEventListener('htmx:sseMessage', function (e) {
  			if (e.target.classList.contains('symbolAlerts')) {

				// FIXME stale to nehraje spolehlive
				alert.currentTime = 0;
				alert.play();
				
		 		var notificationsElt = document.getElementById("notifications");
		 		var item = document.createElement('div');
		 		item.innerText = e.detail.data;
				item.setAttribute("class", "notification");
		 		notificationsElt.appendChild(item);
		 		
				setTimeout(function() {
		 	  		notificationsElt.removeChild(item);
		 		}, 30000);
		 		
  				if (Notification.permission === 'granted') {
  		  			new Notification("TRALON Alert: Momentum symbol",
  		  				{ 
  		  					icon: document.location.origin + "/favicon.ico",
  		  					body: e.detail.data 
  		  				});
  				}
  			}
  		});
  		
  	};