// VIBE MongoDB Initialization Script
// This runs automatically when MongoDB container starts for the first time

// Create all VIBE databases and indexes

db = db.getSiblingDB('vibe_messaging');
db.createCollection('messages');
db.createCollection('conversations');
db.messages.createIndex({ conversationId: 1, createdAt: -1 });
db.messages.createIndex({ senderId: 1 });
db.conversations.createIndex({ participantIds: 1, lastMessageAt: -1 });
db.conversations.createIndex({ isGroup: 1 });
print('✓ vibe_messaging database initialized');

db = db.getSiblingDB('vibe_feed');
db.createCollection('posts');
db.createCollection('comments');
db.createCollection('user_profiles');
db.createCollection('statuses');
db.posts.createIndex({ isActive: 1, createdAt: -1 });
db.posts.createIndex({ authorId: 1, isActive: 1 });
db.statuses.createIndex({ authorId: 1, isActive: 1 });
db.statuses.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });
print('✓ vibe_feed database initialized');

db = db.getSiblingDB('vibe_rooms');
db.createCollection('rooms');
db.rooms.createIndex({ isLive: 1, isPrivate: 1, createdAt: -1 });
db.rooms.createIndex({ hostId: 1 });
db.rooms.createIndex({ participantIds: 1 });
db.rooms.createIndex({ vanishAt: 1 }, { expireAfterSeconds: 0 });
print('✓ vibe_rooms database initialized');

db = db.getSiblingDB('vibe_notifications');
db.createCollection('notifications');
db.notifications.createIndex({ recipientId: 1, createdAt: -1 });
db.notifications.createIndex({ recipientId: 1, isRead: 1 });
db.notifications.createIndex({ createdAt: 1 }, { expireAfterSeconds: 2592000 });
print('✓ vibe_notifications database initialized');

print('');
print('✅ VIBE MongoDB fully initialized!');
