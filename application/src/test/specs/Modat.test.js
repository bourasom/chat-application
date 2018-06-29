import { shallow } from 'vue-test-utils';
import Modal from '../../main/webapp/vue-app/components/modal/Modal';
import {chatConstants} from '../../main/webapp/vue-app/chatConstants.js';

global.fetch = jest.fn().mockImplementation(() => {
  var p = new Promise((resolve) => {
    resolve({
      text: function() {
        return '';
      },
      json: function() {
        return {};
      }
    });
  });
  return p;
});

function getComponent() {
  return shallow(Modal, {
    propsData: {
      displayClose: true,
      title: 'Modal title',
      modalClass: 'modalTestClass'
    },
    mocks: {
      $t: (key, params) => {
        return `${key} params: ${params ? JSON.stringify(params) :''}`;
      },
      $constants : chatConstants,
      mq: 'desktop'
    }
  });
}

describe('Modal.test.js', () => {

  it('test displayed DOM', () => {
    const cmp = getComponent();
    cmp.update();
    expect(cmp.html()).toContain('Modal title');
    expect(cmp.html()).toContain('modalTestClass');
    expect(cmp.findAll('.uiIconClose')).toHaveLength(1);
  });

  it('test close modal', () => {
    const cmp = getComponent();
    cmp.find('.uiIconClose').trigger('click');
    expect(cmp.emitted('modal-closed')).toHaveLength(1);
  });

});