(function () {
    'use strict';

    const patterns = {
        name: /^(?=.{2,50}$)(?!.*\s{2,})([A-Z][a-z]+)(\s[A-Z][a-z]+)*(\s[A-Z])?$/,
        username: /^[A-Za-z][A-Za-z0-9]*$/,
        email: /^(?:"(?:[^\x00-\x1F\x22\x5C\x7F-\xFF]|\\[\x20-\x7E])*"|[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]{1,63}(?:\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+){0,31})@(?:(?=.{1,253}$)(?!.*--)(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?\.)+[\p{L}]{2,63})$/u,
        phone: /^0[1-9]\d{8}$/,
        address: /^(?=.{6,200}$)(?!.*[.,#/^()'\-]{2,})(?!\s)(?!.*\s$)[A-Za-z0-9\s.,#/^()'\-]+$/,
        password: /^(?=.*[A-Za-z])(?=.*\d).+$/
    };

    // Error messages
    const messages = {
        name: 'Name must start with uppercase (e.g., Nguyen Van A)',
        username: 'Username must be 5-20 chars, letters and numbers only',
        email: 'Invalid email format (e.g., user@example.com)',
        phone: 'Phone must be 10 digits starting with 0',
        address: 'Address must be 6-200 chars',
        password: 'Password must be 6-20 chars with letters and numbers',
        confirmPassword: 'Passwords do not match'
    };

    function showError(input, message) {
        const errorSpan = document.querySelector(`#error_${input.id}`);
        if (errorSpan) {
            errorSpan.textContent = message;
            errorSpan.style.display = 'block';
            input.style.borderColor = '#d32f2f';
            input.style.borderWidth = '2px';
        }
    }

    function clearError(input) {
        const errorSpan = document.querySelector(`#error_${input.id}`);
        if (errorSpan) {
            errorSpan.textContent = '';
            errorSpan.style.display = 'none';
            input.style.borderColor = '#ddd';
            input.style.borderWidth = '1px';
        }
    }

    function clearAllErrors() {
        const errorSpans = document.querySelectorAll('.form-error');
        errorSpans.forEach(span => {
            span.textContent = '';
            span.style.display = 'none';
        });
        const inputs = document.querySelectorAll('#createStaffForm input');
        inputs.forEach(input => {
            input.style.borderColor = '#ddd';
            input.style.borderWidth = '1px';
        });
    }

    function validateField(input) {
        const fieldName = input.id.replace('staff_', '');
        const value = input.value.trim();

        if (!value) {
            showError(input, 'This field is required');
            return false;
        }

        if (fieldName === 'name') {
            if (value.length < 2 || value.length > 50) {
                showError(input, 'Name must be 2-50 characters');
                return false;
            }
            if (!patterns.name.test(value)) {
                showError(input, messages.name);
                return false;
            }
        }

        if (fieldName === 'username') {
            if (value.length < 5 || value.length > 20) {
                showError(input, 'Username must be 5-20 characters');
                return false;
            }
            if (!patterns.username.test(value)) {
                showError(input, 'Username must start with a letter');
                return false;
            }
        }

        if (fieldName === 'email' && !patterns.email.test(value)) {
            showError(input, messages.email);
            return false;
        }

        if (fieldName === 'phone' && !patterns.phone.test(value)) {
            showError(input, messages.phone);
            return false;
        }

        if (fieldName === 'address') {
            if (value.length < 6 || value.length > 200) {
                showError(input, messages.address);
                return false;
            }
            if (!patterns.address.test(value)) {
                showError(input, 'Invalid address format');
                return false;
            }
        }

        if (fieldName === 'password') {
            if (value.length < 6 || value.length > 20) {
                showError(input, 'Password must be 6-20 characters');
                return false;
            }
            if (!patterns.password.test(value)) {
                showError(input, 'Password must contain letters and numbers');
                return false;
            }
        }

        if (fieldName === 'confirmPassword') {
            const passwordInput = document.querySelector('#staff_password');
            if (value !== passwordInput.value) {
                showError(input, messages.confirmPassword);
                return false;
            }
        }

        clearError(input);
        return true;
    }

    function initializeValidation() {
        const fields = [
            'staff_name',
            'staff_username',
            'staff_email',
            'staff_phone',
            'staff_address',
            'staff_password',
            'staff_confirmPassword'
        ];

        fields.forEach(fieldId => {
            const input = document.querySelector(`#${fieldId}`);
            if (input) {
                input.addEventListener('blur', function () {
                    validateField(this);
                });
                input.addEventListener('input', function () {
                    if (this.id === 'staff_confirmPassword') {
                        return;
                    }
                    if (this.value.trim()) {
                        validateField(this);
                    }
                });
                input.addEventListener('focus', function () {
                    clearError(this);
                });
            }
        });
    }

    function handleAddStaffClick() {
        const addStaffBtn = document.querySelector('#addStaffBtn');
        if (addStaffBtn) {
            addStaffBtn.addEventListener('click', function (e) {
                e.preventDefault();
                const modal = document.querySelector('#createStaffModal');
                if (modal) {
                    $(modal).modal('show');
                }
            });
        }
    }

    function handleFormSubmit() {
        const form = document.querySelector('#createStaffForm');
        if (!form) return;

        form.addEventListener('submit', function (e) {
            e.preventDefault();

            // Clear all previous errors
            clearAllErrors();

            // Validate all fields
            const fields = [
                'staff_name',
                'staff_username',
                'staff_email',
                'staff_phone',
                'staff_address',
                'staff_password',
                'staff_confirmPassword'
            ];

            let isValid = true;
            fields.forEach(fieldId => {
                const input = document.querySelector(`#${fieldId}`);
                if (input && !validateField(input)) {
                    isValid = false;
                }
            });

            if (!isValid) {
                Swal.fire({
                    icon: 'error',
                    title: 'Validation Error',
                    text: 'Please fix the errors above'
                });
                return;
            }

            // Collect form data
            const formData = {
                name: document.querySelector('#staff_name').value.trim(),
                username: document.querySelector('#staff_username').value.trim(),
                email: document.querySelector('#staff_email').value.trim(),
                phone: document.querySelector('#staff_phone').value.trim(),
                address: document.querySelector('#staff_address').value.trim(),
                password: document.querySelector('#staff_password').value.trim(),
                confirmPassword: document.querySelector('#staff_confirmPassword').value.trim()
            };

            // Submit via fetch
            const submitBtn = document.querySelector('#createStaffBtn');
            const originalText = submitBtn.innerHTML;
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<i class="fa fa-spinner fa-spin"></i> Creating...';

            fetch('/admin/create-staff', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formData)
            })
                .then(response => {
                    if (!response.ok) {
                        throw new Error('Network response was not ok');
                    }
                    return response.json();
                })
                .then(data => {
                    if (data.success) {
                        Swal.fire({
                            icon: 'success',
                            title: 'Success!',
                            text: data.message || 'Staff created successfully',
                            confirmButtonText: 'OK'
                        }).then(() => {
                            $('#createStaffModal').modal('hide');
                            location.reload();
                        });
                    } else {
                        // Display backend errors in form if available
                        if (data.errors && typeof data.errors === 'object') {
                            displayBackendErrors(data.errors);
                        }

                        // Show error alert
                        Swal.fire({
                            icon: 'error',
                            title: 'Validation Error',
                            text: data.message || 'Please fix the errors and try again'
                        });
                    }
                })
                .catch(error => {
                    console.error('Error:', error);
                    Swal.fire({
                        icon: 'error',
                        title: 'Error',
                        text: 'An error occurred. Please try again.'
                    });
                })
                .finally(() => {
                    submitBtn.disabled = false;
                    submitBtn.innerHTML = originalText;
                });
        });
    }

    function displayBackendErrors(errors) {
        if (!errors || typeof errors !== 'object') {
            return;
        }

        // Map backend error keys to form field IDs
        const fieldMapping = {
            'name': 'staff_name',
            'username': 'staff_username',
            'email': 'staff_email',
            'phone': 'staff_phone',
            'address': 'staff_address',
            'password': 'staff_password',
            'confirmPassword': 'staff_confirmPassword'
        };

        // Display each error
        Object.keys(errors).forEach(key => {
            const fieldId = fieldMapping[key];
            if (fieldId) {
                const input = document.querySelector(`#${fieldId}`);
                if (input) {
                    showError(input, errors[key]);
                }
            }
        });

        // Scroll to first error
        const firstError = document.querySelector('.form-error[style*="display: block"]');
        if (firstError) {
            firstError.scrollIntoView({behavior: 'smooth', block: 'center'});
        }
    }

    function resetFormOnModalHidden() {
        const modal = document.querySelector('#createStaffModal');
        if (modal) {
            $(modal).on('hidden.bs.modal', function () {
                const form = document.querySelector('#createStaffForm');
                if (form) {
                    form.reset();
                }
                clearAllErrors();
            });
        }
    }

    // Initialize when DOM is ready
    document.addEventListener('DOMContentLoaded', function () {
        initializeValidation();
        handleAddStaffClick();
        handleFormSubmit();
        resetFormOnModalHidden();
    });

})();