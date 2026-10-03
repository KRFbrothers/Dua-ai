// Notifications only: intentionally no fetch handler or offline asset cache.
self.addEventListener('notificationclick',event=>{
 event.notification.close();const id=event.notification.data?.chatId;
 const route='chat.html'+(typeof id==='string'?'?chat='+encodeURIComponent(id):'');
 event.waitUntil(clients.openWindow(new URL(route,self.registration.scope).href));
});
