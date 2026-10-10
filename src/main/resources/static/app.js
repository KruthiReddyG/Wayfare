const state = { token: sessionStorage.getItem('wayfareToken'), user: JSON.parse(sessionStorage.getItem('wayfareUser') || 'null'), pendingPage: null };
const pageIds = ['home', 'login', 'register', 'dashboard', 'create-ride', 'my-rides', 'inbox', 'notifications', 'admin', 'profile'];
const pageRoles = { dashboard: ['DRIVER', 'PASSENGER', 'ADMIN'], 'create-ride': ['DRIVER'], 'my-rides': ['DRIVER', 'PASSENGER'], inbox: ['DRIVER'], notifications: ['DRIVER', 'PASSENGER', 'ADMIN'], admin: ['ADMIN'], profile: ['DRIVER', 'PASSENGER', 'ADMIN'] };
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const money = value => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(value || 0);
const dateLabel = value => value ? new Date(`${value}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';

async function api(path, options = {}) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}), ...options.headers };
  const response = await fetch(path, { ...options, headers });
  if (response.status === 204) return null;
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.error || body.message || `Request failed (${response.status})`);
  return body;
}

function toast(message) {
  const item = document.createElement('div'); item.className = 'toast-message'; item.textContent = message;
  $('#toast-host').append(item); setTimeout(() => item.remove(), 3600);
}

function setAccount(user, token) {
  state.user = user; state.token = token;
  if (user) { sessionStorage.setItem('wayfareToken', token); sessionStorage.setItem('wayfareUser', JSON.stringify(user)); }
  else { sessionStorage.removeItem('wayfareToken'); sessionStorage.removeItem('wayfareUser'); }
  $$('.signed-only').forEach(node => node.hidden = !user);
  $$('.signed-out').forEach(node => node.hidden = Boolean(user));
  $$('.driver-only').forEach(node => node.hidden = user?.role !== 'DRIVER');
  $$('[data-for-role]').forEach(node => node.hidden = !user || !node.dataset.forRole.split(',').includes(user.role));
  $('#account-name').textContent = user?.name || 'Account'; $('#avatar').textContent = user?.name?.trim()?.[0]?.toUpperCase() || 'W';
  $$('.nav-link').forEach(node => node.classList.remove('active'));
  renderNavLinks();
  if (user?.role === 'DRIVER') updateRequestCount();
}

function showPage(name) {
  if (!pageIds.includes(name)) name = 'home';
  const allowedRoles = pageRoles[name];
  if (allowedRoles && !state.user) { state.pendingPage = name; toast('Log in to continue.'); showPage('login'); return; }
  if (allowedRoles && !allowedRoles.includes(state.user.role)) { toast('That page is not available for your account.'); showPage(state.user.role === 'ADMIN' ? 'admin' : 'dashboard'); return; }
  $$('.page').forEach(page => page.classList.toggle('active', page.id === `page-${name}`));
  $$('.nav-link').forEach(link => link.classList.toggle('active', link.dataset.page === name));
  if (name === 'dashboard' && state.user) loadDashboard();
  if (name === 'my-rides' && state.user) loadJourneys();
  if (name === 'inbox' && state.user?.role === 'DRIVER') loadInbox();
  if (name === 'notifications' && state.user) loadNotifications();
  if (name === 'admin' && state.user?.role === 'ADMIN') loadAdmin();
  if (name === 'profile' && state.user) loadProfile();
  if (name === 'create-ride' && state.user?.role === 'DRIVER') loadVehicles();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function renderNavLinks() {
  const links = $('#role-page-links');
  if (!links) return;
  const pages = state.user ? [
    ...(state.user.role === 'ADMIN' ? [{ page: 'admin', label: 'Platform overview' }] : [{ page: 'dashboard', label: 'Dashboard' }]),
    ...(state.user.role === 'DRIVER' ? [{ page: 'create-ride', label: 'Offer a ride' }, { page: 'my-rides', label: 'My journeys' }, { page: 'inbox', label: 'Ride requests' }] : []),
    ...(state.user.role === 'PASSENGER' ? [{ page: 'my-rides', label: 'My journeys' }] : []),
    { page: 'notifications', label: 'Notifications' },
    { page: 'profile', label: 'Profile and support' }
  ] : [];
  links.innerHTML = pages.map(item => `<button class="nav-link" data-page="${item.page}">${esc(item.label)}${item.page === 'inbox' ? ' <span class="nav-count" id="request-count"></span>' : ''}</button>`).join('');
}

function requireRole(role) {
  if (!state.user) { toast('Log in to continue.'); showPage('login'); return false; }
  if (role && state.user.role !== role) { toast(`This action is for ${role.toLowerCase()} accounts.`); return false; }
  return true;
}

function rideCard(ride, matched = false) {
  const routeText = `${esc(ride.source)} <span class="route-arrow"><i class="bi bi-arrow-right"></i></span> ${esc(ride.destination)}`;
  return `<article class="ride-card"><div><div class="ride-top"><span class="ride-date">${dateLabel(ride.travelDate)} · ${esc(ride.departureTime?.slice(0, 5))}</span>${matched ? `<span class="match-tag"><i class="bi bi-stars"></i> ${Math.round(ride.matchScore)}% match</span>` : ''}</div><div class="route-line">${routeText}</div><div class="ride-meta"><span><i class="bi bi-person"></i> ${esc(ride.driverName)}</span><span><i class="bi bi-car-front"></i> ${esc(ride.vehicle || 'Vehicle')}</span><span><i class="bi bi-people"></i> ${ride.availableSeats} seat${ride.availableSeats === 1 ? '' : 's'}</span></div></div><div class="ride-bottom"><span class="ride-price">${money(ride.estimatedCost)} <small>estimated total</small></span><button class="button ${matched ? 'button-dark' : 'button-quiet'}" data-request-ride="${ride.id}">Request seat <i class="bi bi-arrow-right"></i></button></div></article>`;
}

async function loadOpenRides() {
  try {
    const rides = await api('/api/rides');
    $('#open-rides').innerHTML = rides.length ? rides.slice(0, 3).map(ride => rideCard(ride)).join('') : '<div class="empty-state">No rides published just yet. Check back soon.</div>';
  } catch (error) { $('#open-rides').innerHTML = `<div class="empty-state">${esc(error.message)}. Start the Spring Boot server to browse rides.</div>`; }
}

async function loadDashboard() {
  const host = $('#dashboard-content');
  if (state.user.role === 'ADMIN') { showPage('admin'); return; }
  const driver = state.user.role === 'DRIVER';
  $('#dash-eyebrow').textContent = driver ? 'DRIVER STUDIO' : 'PASSENGER DASHBOARD';
  $('#dash-title').textContent = `Good to see you, ${state.user.name.split(' ')[0]}.`;
  $('#dash-subtitle').textContent = driver ? 'A little planning can make someone else’s day.' : 'Find a seat, or pick up where your last journey left off.';
  try {
    const [bookings, rides, requests, ratings] = await Promise.all([
      api('/api/bookings/mine').catch(() => []),
      driver ? api('/api/rides/mine').catch(() => []) : Promise.resolve([]),
      driver ? api('/api/requests/inbox').catch(() => []) : Promise.resolve([]),
      driver ? api('/api/ratings/mine').catch(() => []) : Promise.resolve([])
    ]);
    const active = bookings.filter(item => item.status === 'CONFIRMED').length;
    const pending = requests.filter(item => item.status === 'PENDING').length;
    const average = ratings.length ? (ratings.reduce((sum, rating) => sum + rating.stars, 0) / ratings.length).toFixed(1) : '—';
    host.innerHTML = `<div class="stat-strip"><div class="stat-cell"><strong>${active}</strong><span>Confirmed seats</span></div><div class="stat-cell"><strong>${driver ? rides.filter(ride => ride.status === 'OPEN' || ride.status === 'FULL').length : bookings.length}</strong><span>${driver ? 'Rides offered' : 'Journeys booked'}</span></div><div class="stat-cell"><strong>${driver ? average : pending}</strong><span>${driver ? `Driver rating · ${ratings.length} reviews` : 'Requests to review'}</span></div><div class="stat-cell"><strong>${driver ? pending : 'Passenger'}</strong><span>${driver ? 'Requests to review' : 'Your Wayfare role'}</span></div></div><div class="admin-tools"><button class="button button-dark" data-page="${driver ? 'create-ride' : 'home'}">${driver ? 'Offer a ride' : 'Search journeys'} <i class="bi bi-arrow-up-right"></i></button><button class="button button-quiet" data-page="my-rides">View my journeys</button>${driver ? '<button class="button button-quiet" data-page="inbox">Review requests</button>' : ''}<button class="button button-quiet" data-page="profile">Edit profile</button></div><div class="browse-head"><div><span class="section-kicker">${driver ? 'RECENT REVIEWS' : 'RECENT ACTIVITY'}</span><h2>${driver ? 'What passengers are saying.' : 'Your next chapter on the road.'}</h2></div></div><div class="table-list">${driver ? ratings.slice(0, 3).map(rating => `<div class="list-row"><div><h3>${'<i class="bi bi-star-fill"></i>'.repeat(rating.stars)}${'<i class="bi bi-star"></i>'.repeat(5 - rating.stars)} · ${esc(rating.passengerName)}</h3><p>${esc(rating.review || 'No written review')} · ${dateLabel(rating.createdAt?.slice(0, 10))}</p></div></div>`).join('') || '<div class="empty-state">Passenger reviews will appear here after completed rides.</div>' : bookings.slice(0, 3).map(bookingRow).join('') || '<div class="empty-state">No journeys here yet. The road is wide open.</div>'}</div>`;
  } catch (error) { host.innerHTML = `<div class="empty-state">${esc(error.message)}</div>`; }
}

function bookingRow(booking) {
  const request = booking.request || {}; const ride = request.ride || {};
  return `<div class="list-row"><div><h3>${esc(ride.source)} to ${esc(ride.destination)}</h3><p>${dateLabel(ride.travelDate)} · ${esc(ride.departureTime?.slice(0, 5))} · ${state.user.role === 'DRIVER' ? `Passenger: ${esc(request.passenger?.name)}` : `Driver: ${esc(ride.driver?.name)}`}</p></div><span class="status ${booking.status?.toLowerCase()}">${esc(booking.status)}</span><div class="list-actions">${booking.status === 'CONFIRMED' && state.user.role === 'PASSENGER' ? `<button class="button button-quiet" data-cancel-booking="${booking.id}">Cancel seat</button>` : ''}${booking.status === 'COMPLETED' && state.user.role === 'PASSENGER' ? `<button class="button button-quiet" data-rate-booking="${booking.id}">Rate driver</button>` : ''}</div></div>`;
}

async function updateRequestCount() {
  try { const requests = await api('/api/requests/inbox'); const count = requests.filter(item => item.status === 'PENDING').length; $('#request-count').textContent = count || ''; }
  catch { $('#request-count').textContent = ''; }
}

async function loadVehicles() {
  const select = $('#vehicle-select');
  try {
    const vehicles = await api('/api/rides/vehicles/mine');
    select.innerHTML = vehicles.length ? vehicles.map(vehicle => `<option value="${vehicle.id}">${esc(vehicle.makeModel)} · ${esc(vehicle.registrationNumber)} (${vehicle.seatCapacity} seats)</option>`).join('') : '<option value="">Add a vehicle first</option>';
  } catch (error) { toast(error.message); }
}

async function loadJourneys() {
  const host = $('#journeys-list'); host.innerHTML = '<div class="loading-line">Loading journeys...</div>';
  try {
    const bookings = await api('/api/bookings/mine');
    let rows = bookings.map(bookingRow);
    if (state.user.role === 'DRIVER') {
      const rides = await api('/api/rides/mine');
      rows = [...rides.map(ride => `<div class="list-row"><div><h3>${esc(ride.source)} to ${esc(ride.destination)}</h3><p>${dateLabel(ride.travelDate)} · ${esc(ride.departureTime?.slice(0, 5))} · ${ride.availableSeats} seats left · ${money(ride.estimatedCost)} total</p></div><span class="status ${ride.status.toLowerCase()}">${esc(ride.status)}</span><div class="list-actions">${ride.status !== 'COMPLETED' && ride.status !== 'CANCELLED' ? `<button class="button button-quiet" data-complete-ride="${ride.id}">Mark complete</button><button class="button button-quiet" data-cancel-ride="${ride.id}">Cancel ride</button>` : ''}</div></div>`), ...rows];
    } else {
      const requests = await api('/api/requests/mine');
      rows = [...rows, ...requests.map(request => `<div class="list-row"><div><h3>Request: ${esc(request.rideRoute)}</h3><p>Driver response · ${dateLabel(request.createdAt?.slice(0, 10))}</p></div><span class="status ${request.status.toLowerCase()}">${esc(request.status)}</span><div class="list-actions">${request.status === 'PENDING' ? `<button class="button button-quiet" data-cancel-request="${request.id}">Cancel request</button>` : ''}</div></div>` )];
    }
    host.innerHTML = rows.join('') || '<div class="empty-state">No journeys to show yet.</div>';
  } catch (error) { host.innerHTML = `<div class="empty-state">${esc(error.message)}</div>`; }
}

async function loadInbox() {
  const host = $('#inbox-list'); host.innerHTML = '<div class="loading-line">Checking for requests...</div>';
  try {
    const requests = await api('/api/requests/inbox');
    host.innerHTML = requests.length ? requests.map(request => `<div class="list-row"><div><h3>${esc(request.passengerName)} · ${esc(request.rideRoute)}</h3><p>${esc(request.message || 'No note added')} · ${new Date(request.createdAt).toLocaleDateString()}</p></div><span class="status ${request.status.toLowerCase()}">${esc(request.status)}</span><div class="list-actions">${request.status === 'PENDING' ? `<button class="button button-dark" data-decision="${request.id}" data-accepted="true">Accept</button><button class="button button-quiet" data-decision="${request.id}" data-accepted="false">Decline</button>` : ''}</div></div>`).join('') : '<div class="empty-state">No ride requests yet. We’ll put them here when they arrive.</div>';
  } catch (error) { host.innerHTML = `<div class="empty-state">${esc(error.message)}</div>`; }
}

async function loadNotifications() {
  const host = $('#notifications-list');
  try {
    const items = await api('/api/notifications');
    host.innerHTML = items.length ? items.map(item => `<div class="list-row"><div><h3>${esc(item.message)}</h3><p>${new Date(item.createdAt).toLocaleString()}</p></div><span class="status">${item.read ? 'READ' : 'NEW'}</span></div>`).join('') : '<div class="empty-state">You’re all caught up.</div>';
  } catch (error) { host.innerHTML = `<div class="empty-state">${esc(error.message)}</div>`; }
}

async function loadProfile() {
  try {
    const user = await api('/api/profile');
    const form = $('#profile-form'); form.elements.name.value = user.name; form.elements.email.value = user.email; form.elements.phone.value = user.phone || '';
  } catch (error) { toast(error.message); }
}

async function loadAdmin() {
  const host = $('#admin-content'); host.innerHTML = '<div class="loading-line">Loading platform activity...</div>';
  try {
    const [stats, users, rides, complaints, bookings, ratings] = await Promise.all([
      api('/api/admin/dashboard'), api('/api/admin/users'), api('/api/admin/rides'), api('/api/admin/complaints'), api('/api/admin/bookings'), api('/api/admin/ratings')
    ]);
    host.innerHTML = `<div class="stat-strip"><div class="stat-cell"><strong>${stats.users}</strong><span>Registered users</span></div><div class="stat-cell"><strong>${stats.rides}</strong><span>Rides posted</span></div><div class="stat-cell"><strong>${stats.bookings}</strong><span>Bookings</span></div><div class="stat-cell"><strong>${stats.openRequests}</strong><span>Open requests</span></div></div><div class="admin-tools">${['Users', 'Rides', 'Bookings', 'Complaints', 'Ratings'].map(tab => `<button class="button button-quiet" data-admin-tab="${tab.toLowerCase()}">${tab}</button>`).join('')}</div><div id="admin-table-host"></div>`;
    const data = { users, rides, complaints, bookings, ratings }; renderAdminTable('users', data.users);
    $$('.admin-tools [data-admin-tab]').forEach(button => button.addEventListener('click', () => renderAdminTable(button.dataset.adminTab, data[button.dataset.adminTab])));
  } catch (error) { host.innerHTML = `<div class="empty-state">${esc(error.message)}</div>`; }
}

function renderAdminTable(tab, rows) {
  const host = $('#admin-table-host'); if (!host) return;
  if (!rows?.length) { host.innerHTML = '<div class="empty-state">Nothing to show here yet.</div>'; return; }
  let headers; let lines;
  if (tab === 'users') { headers = ['Name', 'Email', 'Role', 'Status', '']; lines = rows.map(user => `<tr><td>${esc(user.name)}</td><td>${esc(user.email)}</td><td>${esc(user.role)}</td><td>${user.active ? 'Active' : 'Inactive'}</td><td><button class="text-action" data-toggle-user="${user.id}" data-active="${!user.active}">${user.active ? 'Deactivate' : 'Activate'}</button></td></tr>`); }
  else if (tab === 'rides') { headers = ['Route', 'Driver', 'Date', 'Seats', 'Status', '']; lines = rows.map(ride => `<tr><td>${esc(ride.source)} → ${esc(ride.destination)}</td><td>${esc(ride.driverName)}</td><td>${dateLabel(ride.travelDate)}</td><td>${ride.availableSeats}</td><td>${esc(ride.status)}</td><td>${['OPEN', 'FULL'].includes(ride.status) ? `<button class="text-action" data-admin-cancel-ride="${ride.id}">Cancel</button>` : ''}</td></tr>`); }
  else if (tab === 'complaints') { headers = ['From', 'Issue', 'Ride', 'Status', '']; lines = rows.map(item => `<tr><td>${esc(item.user?.name)}</td><td>${esc(item.description)}</td><td>${item.ride?.id || '—'}</td><td>${esc(item.status)}</td><td>${item.status !== 'RESOLVED' ? `<button class="text-action" data-resolve-complaint="${item.id}">Resolve</button>` : ''}</td></tr>`); }
  else if (tab === 'ratings') { headers = ['Driver', 'Passenger', 'Stars', 'Review']; lines = rows.map(item => `<tr><td>${esc(item.driver?.name)}</td><td>${esc(item.passenger?.name)}</td><td>${item.stars} / 5</td><td>${esc(item.review || '—')}</td></tr>`); }
  else { headers = ['Passenger', 'Route', 'Date', 'Status']; lines = rows.map(item => `<tr><td>${esc(item.request?.passenger?.name)}</td><td>${esc(item.request?.ride?.source)} → ${esc(item.request?.ride?.destination)}</td><td>${dateLabel(item.request?.ride?.travelDate)}</td><td>${esc(item.status)}</td></tr>`); }
  host.innerHTML = `<table class="admin-table"><thead><tr>${headers.map(header => `<th>${header}</th>`).join('')}</tr></thead><tbody>${lines.join('')}</tbody></table>`;
}

document.addEventListener('click', async event => {
  const pageButton = event.target.closest('[data-page]');
  if (pageButton) { showPage(pageButton.dataset.page); return; }
  const requestRide = event.target.closest('[data-request-ride]');
  if (requestRide) {
    if (!requireRole('PASSENGER')) return;
    const message = window.prompt('Add a short note for the driver (optional):') || '';
    try { await api('/api/requests', { method: 'POST', body: JSON.stringify({ rideId: Number(requestRide.dataset.requestRide), message }) }); toast('Request sent to the driver.'); requestRide.disabled = true; requestRide.textContent = 'Request sent'; }
    catch (error) { toast(error.message); }
    return;
  }
  const decision = event.target.closest('[data-decision]');
  if (decision) {
    try { await api(`/api/requests/${decision.dataset.decision}`, { method: 'PATCH', body: JSON.stringify({ accepted: decision.dataset.accepted === 'true' }) }); toast(decision.dataset.accepted === 'true' ? 'Seat confirmed. The passenger has been notified.' : 'Request declined.'); loadInbox(); updateRequestCount(); }
    catch (error) { toast(error.message); }
    return;
  }
  const cancelBooking = event.target.closest('[data-cancel-booking]');
  if (cancelBooking && confirm('Cancel this confirmed seat?')) { try { await api(`/api/bookings/${cancelBooking.dataset.cancelBooking}`, { method: 'DELETE' }); toast('Seat cancelled and returned to the ride.'); loadJourneys(); } catch (error) { toast(error.message); } return; }
  const cancelRequest = event.target.closest('[data-cancel-request]');
  if (cancelRequest) { try { await api(`/api/requests/${cancelRequest.dataset.cancelRequest}`, { method: 'DELETE' }); toast('Request cancelled.'); loadJourneys(); } catch (error) { toast(error.message); } return; }
  const cancelRide = event.target.closest('[data-cancel-ride]');
  if (cancelRide && confirm('Cancel this ride? Passengers with confirmed seats will be notified.')) { try { await api(`/api/rides/${cancelRide.dataset.cancelRide}`, { method: 'DELETE' }); toast('Ride cancelled.'); loadJourneys(); } catch (error) { toast(error.message); } return; }
  const completeRide = event.target.closest('[data-complete-ride]');
  if (completeRide && confirm('Mark this ride as completed? Passengers can then leave a rating.')) { try { await api(`/api/rides/${completeRide.dataset.completeRide}/complete`, { method: 'POST' }); toast('Ride marked complete.'); loadJourneys(); } catch (error) { toast(error.message); } return; }
  const rateBooking = event.target.closest('[data-rate-booking]');
  if (rateBooking) {
    const stars = Number(prompt('Rate your driver from 1 to 5 stars:'));
    if (!stars || stars < 1 || stars > 5) return;
    const review = prompt('Leave a short review (optional):') || '';
    try { await api('/api/ratings', { method: 'POST', body: JSON.stringify({ bookingId: Number(rateBooking.dataset.rateBooking), stars, review }) }); toast('Thanks for sharing your experience.'); rateBooking.remove(); }
    catch (error) { toast(error.message); }
    return;
  }
  const toggleUser = event.target.closest('[data-toggle-user]');
  if (toggleUser) { try { await api(`/api/admin/users/${toggleUser.dataset.toggleUser}/active`, { method: 'PATCH', body: JSON.stringify({ active: toggleUser.dataset.active === 'true' }) }); loadAdmin(); } catch (error) { toast(error.message); } return; }
  const adminRide = event.target.closest('[data-admin-cancel-ride]');
  if (adminRide && confirm('Cancel this ride and notify confirmed passengers?')) { try { await api(`/api/admin/rides/${adminRide.dataset.adminCancelRide}`, { method: 'DELETE' }); toast('Ride cancelled.'); loadAdmin(); } catch (error) { toast(error.message); } return; }
  const complaint = event.target.closest('[data-resolve-complaint]');
  if (complaint) { try { await api(`/api/admin/complaints/${complaint.dataset.resolveComplaint}/status`, { method: 'PATCH', body: JSON.stringify({ status: 'RESOLVED' }) }); toast('Complaint marked resolved.'); loadAdmin(); } catch (error) { toast(error.message); } }
});

$('#search-form').addEventListener('submit', async event => {
  event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget)); values.seats = Number(values.seats);
  try {
    const rides = await api('/api/rides/search', { method: 'POST', body: JSON.stringify(values) });
    $('#results-title').textContent = `${rides.length} ${rides.length === 1 ? 'ride' : 'rides'} near your route`;
    $('#results-list').innerHTML = rides.length ? rides.map(ride => rideCard(ride, true)).join('') : '<div class="empty-state">No close matches for this date. Try a nearby time or check again later.</div>';
    $('#results-section').hidden = false; $('#results-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) { toast(error.message); }
});
$('#clear-results').addEventListener('click', () => { $('#results-section').hidden = true; $('#results-list').innerHTML = ''; });

$('#login-form').addEventListener('submit', async event => {
  event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget));
  try { const result = await api('/api/auth/login', { method: 'POST', body: JSON.stringify(values) }); setAccount({ id: result.id, name: result.name, email: result.email, role: result.role }, result.token); toast(`Welcome back, ${result.name.split(' ')[0]}.`); const destination = state.pendingPage; state.pendingPage = null; showPage(destination && pageRoles[destination]?.includes(result.role) ? destination : result.role === 'ADMIN' ? 'admin' : 'dashboard'); }
  catch (error) { toast(error.message); }
});

$('#register-form').addEventListener('submit', async event => {
  event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget));
  try { const result = await api('/api/auth/register', { method: 'POST', body: JSON.stringify(values) }); setAccount({ id: result.id, name: result.name, email: result.email, role: result.role }, result.token); state.pendingPage = null; toast('Your Wayfare account is ready.'); showPage('dashboard'); }
  catch (error) { toast(error.message); }
});

$('#vehicle-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget || document.getElementById('vehicle-form');
  if (!form) return;
  const values = Object.fromEntries(new FormData(form));
  values.seatCapacity = Number(values.seatCapacity);
  try {
    await api('/api/rides/vehicles', { method: 'POST', body: JSON.stringify(values) });
    form.reset();
    toast('Vehicle added.');
    loadVehicles();
  } catch (error) { toast(error.message); }
});

$('#create-ride-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget || document.getElementById('create-ride-form');
  if (!form) return;
  const values = Object.fromEntries(new FormData(form));
  values.vehicleId = Number(values.vehicleId); values.availableSeats = Number(values.availableSeats); values.estimatedCost = Number(values.estimatedCost);
  try { await api('/api/rides', { method: 'POST', body: JSON.stringify(values) }); form.reset(); toast('Your ride is now listed.'); showPage('my-rides'); }
  catch (error) { toast(error.message); }
});

$('#profile-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget || document.getElementById('profile-form');
  if (!form) return;
  const values = Object.fromEntries(new FormData(form));
  try { const user = await api('/api/profile', { method: 'PUT', body: JSON.stringify(values) }); setAccount({ ...state.user, name: user.name }, state.token); toast('Profile saved.'); }
  catch (error) { toast(error.message); }
});

$('#complaint-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget || document.getElementById('complaint-form');
  if (!form) return;
  const values = Object.fromEntries(new FormData(form));
  try { await api('/api/complaints', { method: 'POST', body: JSON.stringify(values) }); form.reset(); toast('Your report has been sent to the platform team.'); }
  catch (error) { toast(error.message); }
});

$('#logout').addEventListener('click', () => { setAccount(null, null); toast('You have logged out.'); showPage('home'); });
$('#account-name').addEventListener('click', () => showPage('profile'));
if (state.user && state.token) setAccount(state.user, state.token); else setAccount(null, null);
const dateInput = $('#search-form [name="travelDate"]'); dateInput.min = new Date().toISOString().slice(0, 10); dateInput.value = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
$('#create-ride-form [name="travelDate"]').min = dateInput.min;
loadOpenRides();