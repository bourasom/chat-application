<template>
  <div
    v-if="contact && Object.keys(contact).length !== 0 && (contact.isEnabledUser === 'true' || contact.isEnabledRoom === 'true')"
    :class="{'is-apps-closed': appsClosed}"
    class="chat-message-composer">
    <div v-if="!miniChat">
      <div v-show="!appsClosed" class="apps-container justify-center">
        <div
          v-for="app in composerApplications"
          :key="app.key"
          class="apps-item"
          @click="openAppModal(app)">
          <div class="apps-item-icon"><i :class="app.iconClass"></i></div>
          <div v-if="mq==='desktop'" class="apps-item-label">{{ $t(app.labelKey) }}</div>
        </div>
      </div>
    </div>
    <div class="composer-container">
      <div class="composer-box">
        <div v-if="!miniChat" class="composer-action">
          <div class="action-emoji">
            <i
              v-exo-tooltip.top="$t('exoplatform.chat.emoji.tip')"
              class="uiIconChatSmile"
              @click.prevent.stop="showEmojiPanel = !showEmojiPanel"></i>
            <div v-show="showEmojiPanel" class="composer-emoji-panel popover top">
              <div class="arrow"></div>
              <span
                v-for="emoji in getEmoticons"
                :key="emoji.keys[0]"
                :class="emoji.class"
                class="chat-emoticon"
                @click="selectEmoji(emoji)"></span>
            </div>
          </div>
          <div
            v-exo-tooltip.top="$t('exoplatform.chat.collaborative.actions.tip')"
            class="action-apps"
            @click="appsClosed = !appsClosed">
            <i class="uiIconPlusCircled"></i>
          </div>
        </div>
        <input
          v-if="miniChat"
          id="messageComposerArea"
          ref="messageComposerArea"
          name="messageComposerArea"
          type="text"
          autofocus
          @keydown.enter="preventDefault"
          @keypress.enter="preventDefault"
          @keyup.enter="sendMessageWithKey">
        <div
          v-else
          id="messageComposerArea"
          ref="messageComposerArea"
          contenteditable="true"
          name="messageComposerArea"
          @keydown.enter="checkIfMentioning"
          @keypress.enter="preventDefault"
          @keyup.enter="sendMessageWithKey"
          @keyup.up="editLastMessage"
          @keyup="resizeTextarea($event)"
          @paste="paste"></div>
        <div
          v-exo-tooltip.top="$t('exoplatform.chat.send')"
          v-if="!miniChat"
          class="composer-action">
          <div class="action-send" @click="sendMessage">
            <i class="uiIconSend"></i>
          </div>
        </div>
      </div>
    </div>
    <exo-chat-apps-modal
      v-if="appsModal.isOpned"
      :app="appsModal.app"
      :title="appsModal.title"
      :contact="contact"
      :room-id="contact.room"
      @modal-closed="appsModal.isOpned = false" />
  </div>
</template>

<script>
import * as chatServices from '../chatServices';
import {EMOTICONS} from '../extension';
import {chatConstants} from '../chatConstants';
import {getUserAvatar} from '../chatServices';

export default {
  props: {
    composersApplications: {
      type: Object,
      default: function () {
        return {};
      }
    },
    miniChat: {
      type: Boolean,
      default: false
    },
    contact: {
      type: Object,
      default: function () {
        return {};
      }
    },
    userSettings: {
      type: Object,
      default: function () {
        return {};
      }
    }
  },
  data() {
    return {
      appsModal: {
        title: '',
        isOpned: false
      },
      appsClosed: true,
      composerApplications: [],
      showEmojiPanel: false,
      participants: [],
      mentionedUsers: [],
      userAvatar: '',
      chatMessage: {},
      mentioningInProgress: false
    };
  },
  computed: {
    getEmoticons() {
      if (eXo && eXo.chat && eXo.chat.room && eXo.chat.room.extraEmoticons) {
        return EMOTICONS.concat(eXo.chat.room.extraEmoticons);
      } else if (EMOTICONS) {
        return EMOTICONS;
      } else {
        return [];
      }
    },
  },
  watch: {
    userSettings() {
      this.composerApplications = this.composersApplications;
    },
    chatMessage() {
      if (this.$refs.messageComposerArea) {
        this.$refs.messageComposerArea.innerHTML = this.chatMessage.msg;
      }
    }
  },
  updated() {
    this.composerApplications = this.composersApplications;
    if (this.contact) {
      if (this.contact.isEnabledUser === 'true' || this.contact.isEnabledUser === 'null') {
        this.$nextTick(() => {
          this.$refs.messageComposerArea.focus();
          this.composerApplications.forEach(application => {
            if (application.mount) {
              application.mount($, chatServices);
            }
          });
          this.initSuggester();
        });
      }
    }
  },
  created() {
    document.addEventListener('keyup', this.closeApps);
    document.addEventListener('click', this.closeEmojiPanel);
    document.addEventListener(chatConstants.ACTION_MESSAGE_SEND, this.putFocusOnComposer);
    document.addEventListener(chatConstants.ACTION_MESSAGE_DELETE, this.putFocusOnComposer);
    document.addEventListener(chatConstants.ACTION_MESSAGE_QUOTE, this.quoteMessage);
    this.$root.$on('edit-chat-message', messageToEdit => {
      this.chatMessage = messageToEdit;
      this.$refs.messageComposerArea.innerHTML = this.chatMessage.msg;
      this.putFocusOnComposer();
    });
  },
  destroyed() {
    document.removeEventListener('keyup', this.closeApps);
    document.removeEventListener('click', this.closeEmojiPanel);
    document.removeEventListener(chatConstants.ACTION_MESSAGE_SEND, this.putFocusOnComposer);
    document.removeEventListener(chatConstants.ACTION_MESSAGE_DELETE, this.putFocusOnComposer);
    document.removeEventListener(chatConstants.ACTION_MESSAGE_QUOTE, this.quoteMessage);
  },
  methods: {
    initSuggester() {
      const $messageSuggestor = $('#messageComposerArea');
      const component = this;
      const suggesterData = {
        type: 'mix',
        create: false,
        createOnBlur: false,
        highlight: false,
        openOnFocus: false,
        sourceProviders: ['exo:chatuser'],
        valueField: 'name',
        labelField: 'fullname',
        searchField: ['fullname', 'name'],
        closeAfterSelect: true,
        dropdownParent: 'body',
        hideSelected: true,
        renderMenuItem: function (item) {
          const avatar = getUserAvatar(item.name);
          const defaultAvatar = '/chat/img/room-default.jpg';
          return `<img src="${avatar}" onerror="this.src='${defaultAvatar}'" width="20px" height="20px">
                      ${chatServices.escapeHtml(item.fullname)}<span style="float: right" class="chat-status-task chat-status-'+item.status+'"></span>`;
        },
        /* eslint-disable no-template-curly-in-string */
        renderItem: '<div class="uiMention">' +
                '@${fullname}' +
                '      <span class="remove"><i class="uiIconClose"></i></span>' +
                '      </div>',
        providers: {
          'exo:chatuser': function (query, callback) {
            if (!query || !query.trim().length) {
              return callback();
            }
            chatServices.getUsersToMention(eXo.chat.userSettings, component.contact, query).then(function (data) {
              if (data && data.users) {
                chatServices.getRoomParticipantsToSuggest(data.users).then(users => {
                  for (let i = 0; i < users.length; i++) {
                    const index = component.participants.findIndex(user => user.name === users[i].name);
                    if (index === -1){
                      component.participants.push(users[i]);
                    }
                  }
                  callback(users.filter(user => user.name !== eXo.chat.userSettings.username));
                });
              }
            });
          }
        },
      };
      //init suggester
      $messageSuggestor.suggester(suggesterData);
    },
    closeApps(e) {
      const ESC_KEY = 27;
      if (e.keyCode === ESC_KEY) {
        this.appsClosed = true;
      }
    },
    closeEmojiPanel() {
      this.showEmojiPanel = false;
    },
    selectEmoji(emoji) {
      let emojiKey = emoji.keys[0];
      const $composer = $(this.$refs.messageComposerArea);
      emojiKey = ` ${emojiKey} `;
      $composer.insertAtCaret(emojiKey);
      this.closeEmojiPanel();
    },
    preventDefault(event) {
      if (event.keyCode === chatConstants.ENTER_CODE_KEY) {
        event.stopPropagation();
        event.preventDefault();
      }
    },
    appendAlternativeToImage(str, index, stringToAdd) {
      return (str.substring(0, index) + stringToAdd + str.substring(index, str.length)) || '';
    },
    sendMessage() {
      let newMessage = this.$refs.messageComposerArea.innerHTML;
      if (newMessage.indexOf('@') > -1) {
        const tab = newMessage.split('<br>');
        newMessage = '';
        for (const elm of tab) {
          let element = elm;
          if (element.indexOf('@') > -1) {
            element = this.checkMention(element);
          }
          newMessage = newMessage.concat(element).concat('<br>');
        }
      }
      if (!newMessage || !newMessage.trim()) {
        return;
      }
      const imgTags = newMessage.match(/<img (alt(="")?)?[^>]*>/g);
      if (imgTags) {
        // create a tab containing the message elements
        const allMessageContentTab = newMessage.split(/<img (alt(="")?)?[^>]*>/g);
        imgTags.forEach(tag =>{
          // delete al="" if exists
          let img = tag.match(/alt="" /g) ? tag.replace(/alt="" /g, '') : tag;
          const alt = img.match(/alt="([^"]*)"/g);
          // append the alt attribute before the src
          img = alt ? this.appendAlternativeToImage(img, img.indexOf('src'), `${alt} `) : img;
          // keep the same message elements order for well displaying
          allMessageContentTab[allMessageContentTab.findIndex(e => !e)] = img;
        });
        // create new string contains all the message elements with same order
        newMessage = allMessageContentTab.join('').toString().replaceAll(/<img (alt(="")?)? *src="/g,`<img alt="${this.$t('exoplatform.chat.team.msg.img.alt')}" src="`);
      }
      const message = {
        msgId: this.chatMessage.msgId,
        message: newMessage.trim().replace(/(&nbsp;|<br>|<br \/>)$/g, ''),
        room: this.contact.room,
        clientId: new Date().getTime().toString(),
        timestamp: Date.now(),
        user: eXo.chat.userSettings.username
      };
      newMessage = newMessage.replace(/<img src="data:image\/.*;base64[^>]*>/g,'');
      let found = false;
      if (!this.miniChat) {
        // shortcuts for specific applications actions
        this.composerApplications.forEach(application => {
          if (application?.shortcutMatches && application?.shortcutMatches(newMessage)) {
            if (application.shortcutCallback) {
              found = true;
              application.shortcutCallback(chatServices, $, newMessage, this.contact);
            } else if (application.shortcutTriggeredEvent) {
              found = true;
              document.dispatchEvent(new CustomEvent(application.shortcutTriggeredEvent, {
                detail: {
                  msg: newMessage,
                  contact: this.contact
                }
              }));
            }
          }
        });
      }
      if (!found) {
        if (message.msgId) {
          this.$emit('message-modified', message);
        } else {
          this.$emit('message-written', message);
        }
      }
      chatServices.getReceiversForMessagePushNotif(this.userSettings,this.contact.room,).then(data=>{
        chatServices.sendMessageReceivedNotification(this.contact.room, this.contact.fullName,message.message,data);
      });
      this.$refs.messageComposerArea.innerHTML = '';
      this.chatMessage = { msgId: '', msg: '' };
      this.mentioningInProgress = false;
    },
    sendMessageWithKey(event) {
      if (event && event.keyCode === chatConstants.ENTER_CODE_KEY && !this.mentioningInProgress) {
        if (event.ctrlKey || event.altKey || event.shiftKey) {
          $(this.$refs.messageComposerArea).insertAtCaret('\n');
        } else {
          this.sendMessage();
        }
      }
    },
    checkIfMentioning() {
      this.mentioningInProgress = this.$refs.messageComposerArea.lastElementChild?.className === 'atwho-query' && !this.$refs.messageComposerArea.lastChild.wholeText;
    },
    quoteMessage(e) {
      const quotedMessage = e.detail;
      const composer = $(this.$refs.messageComposerArea);
      if (!quotedMessage) {
        return;
      }
      let messageToSend = quotedMessage.msg ? quotedMessage.msg : quotedMessage.message;
      if (!messageToSend) {
        return;
      }
      messageToSend = messageToSend.replace(/<br\/>/g, '\n');
      messageToSend = $('<div />').html(messageToSend).text();
      messageToSend = `[quote=${quotedMessage.fullname}] ${messageToSend} [/quote]`;
      composer.insertAtCaret(messageToSend);
    },
    openAppModal(app) {
      this.appsClosed = true;
      this.appsModal.app = app;
      this.appsModal.title = this.$t(app.labelKey);
      this.appsModal.isOpned = true;
    },
    editLastMessage() {
      const newMessage = this.$refs.messageComposerArea.innerHTML;

      if (!newMessage || !newMessage.trim().length) {
        this.$refs.messageComposerArea.innerHTML = '';
        this.putFocusOnComposer();
        document.dispatchEvent(new CustomEvent(chatConstants.ACTION_MESSAGE_EDIT_LAST));
      }
    },
    putFocusOnComposer() {
      this.$refs.messageComposerArea.focus();
    },
    resizeTextarea(e) {
      if (this.mq !== 'mobile') {return;}
      const BORDER_SIZE = 2;
      const INITIAL_HEIGHT = '40px';
      const elem = e.target;
      elem.style.height = INITIAL_HEIGHT;
      elem.style.height = `${elem.scrollHeight + BORDER_SIZE}px`;
      elem.scrollTop = elem.scrollHeight;
    },
    checkMention(message) {
      message = $('<div />').html(message).text();
      message = message.replace(/\s\s+/g, ' ');
      message = this.encodeHTMLEntities(message);
      for (const element of this.participants) {
        if (message.includes(`@${element.fullname}`) ){
          this.mentionedUsers.push(element.name);
          const profil = chatServices.getUserProfileLink(element.name);
          const html = `<a href='${profil}' target='_blank'>@${element.fullname}</a>`;
          message = message.replaceAll(`@${element.fullname}`, html);
        }
      }
      chatServices.sendMentionNotification(this.contact.room, this.contact.fullName, this.mentionedUsers);
      this.mentionedUsers = [];
      return message;
    },
    encodeHTMLEntities(text) {
      const textArea = document.createElement('p');
      textArea.innerText = text;
      return textArea.innerHTML;
    },
    paste(e) {
      // consider the first item (can be easily extended for multiple items)
      const item = e.clipboardData.items[0];
      //test if the type of item e
      if (item.type.indexOf('image') === 0) {
        const pastedImage = item.getAsFile();
        const reader = new FileReader();
        reader.onload = function (event) {
          $('#messageComposerArea').src = event.target.result;
        };
        reader.readAsDataURL(pastedImage);
      } else {
        // cancel paste
        e.preventDefault();
        // get text representation of clipboard
        this.text = this.encodeHTMLEntities((e.originalEvent || e).clipboardData.getData('text/plain'));
        // insert text manually
        $(this.$refs.messageComposerArea).insertAtCaret(this.text);
      }
    },
  }
};
</script>
